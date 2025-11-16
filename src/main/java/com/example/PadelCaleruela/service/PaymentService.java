package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.PaymentDTO;
import com.example.PadelCaleruela.dto.CheckoutRequest;
import com.example.PadelCaleruela.dto.ConfirmPaymentRequest;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Payment;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.PaymentRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuthService authService;
    private final PricingService pricingService;

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("10.00");

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;   // 👈 inyectamos la key de properties

    // --------------------------------------------------
    // 🔐 HELPERS DE SEGURIDAD
    // --------------------------------------------------

    private void ensureCanAccessReservation(Reservation r) {
        User current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        // USER → solo puede pagar sus reservas
        if (authService.isUser() && !r.getUser().getId().equals(current.getId())) {
            throw new AccessDeniedException("No puedes acceder al pago de esta reserva.");
        }

        // ADMIN → solo dentro de su ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(r.getUser().getAyuntamiento());
        }
    }

    private void ensureCanAccessUser(User target) {
        User current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        if (authService.isUser() && !target.getId().equals(current.getId())) {
            throw new AccessDeniedException("No puedes operar sobre otro usuario.");
        }

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target.getAyuntamiento());
        }
    }

    // --------------------------------------------------
    // 1️⃣ CREAR CHECKOUT (Stripe + Connect)
    // --------------------------------------------------

    public PaymentDTO createCheckout(CheckoutRequest req) {

        Reservation reservation = reservationRepository.findById(req.getReservationId())
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        // 🔐 Seguridad
        ensureCanAccessReservation(reservation);

        if (reservation.isPaid()) {
            throw new IllegalStateException("La reserva ya está pagada.");
        }

        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        ensureCanAccessUser(user);

        // 🏛️ Ayuntamiento (multi-tenant)
        Ayuntamiento ay = reservation.getUser().getAyuntamiento();
        if (ay == null || ay.getStripeAccountId() == null || ay.getStripeAccountId().isBlank()) {
            throw new IllegalStateException("Ayuntamiento sin cuenta de Stripe configurada.");
        }
        String stripeAccountId = ay.getStripeAccountId();

        // 🔄 Reutilizar pago pendiente si existe
        Payment existing = paymentRepository.findByReservation_Id(reservation.getId());
        if (existing != null) {
            return returnExistingPayment(existing);
        }

        // Crear o validar customer en Stripe (en la cuenta del Ayuntamiento)
        String stripeCustomerId = ensureStripeCustomer(user, stripeAccountId);

        // Crear entidad Payment local
        Payment p = new Payment();
        p.setReservation(reservation);
        p.setUser(user);
        p.setProvider(Payment.Provider.STRIPE);
        p.setProviderAccountId(stripeAccountId);  // ⬅️ muy importante para multi-cuenta
        p.setStatus(Payment.Status.PENDING);
        BigDecimal precio = pricingService.calcularPrecio(
                reservation.getStartTime(),
                reservation.getAyuntamiento()
        );

        p.setAmount(precio);
        p.setCurrency("EUR");

        Payment saved = paymentRepository.save(p);

        try {
            // Crear PaymentIntent en la CUENTA del ayuntamiento
            PaymentIntent intent = createStripeIntent(saved, stripeCustomerId, req, stripeAccountId);
            System.out.println("🔥 Creando PaymentIntent para CONNECT account: " + stripeAccountId);

            saved.setPaymentIntentId(intent.getId());
            saved.setPaymentReference(intent.getClientSecret());

            paymentRepository.save(saved);

            // (Opcional) auto-success en modo test
            if ("succeeded".equalsIgnoreCase(intent.getStatus())) {
                handleAutoSuccess(saved, reservation);
            }

            PaymentDTO dto = toDTO(saved);
            dto.setClientSecret(intent.getClientSecret());
            dto.setPaymentIntentId(intent.getId());
            ay.setStripeAccountId(ay.getStripeAccountId().trim());
            return dto;

        } catch (Exception e) {
            throw new RuntimeException("Error creando PaymentIntent", e);
        }
    }

    private PaymentDTO returnExistingPayment(Payment existing) {
        if (existing.getStatus() == Payment.Status.SUCCEEDED) {
            throw new IllegalStateException("Esta reserva ya tiene un pago confirmado.");
        }

        PaymentDTO dto = toDTO(existing);
        dto.setPaymentIntentId(existing.getPaymentIntentId());
        dto.setClientSecret(existing.getPaymentReference());
        return dto;
    }

    // --------------------------------------------------
    // 2️⃣ Confirmar Pago (callback desde frontend)
    // --------------------------------------------------

    @Transactional
    public PaymentDTO confirmPayment(ConfirmPaymentRequest req) {

        Payment payment = paymentRepository.findById(req.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));

        // 🔐 Seguridad
        ensureCanAccessReservation(payment.getReservation());

        if (payment.getStatus() != Payment.Status.PENDING) {
            throw new IllegalStateException("El pago ya fue procesado.");
        }

        if (req.isSuccess()) {
            payment.setStatus(Payment.Status.SUCCEEDED);
            payment.setPaymentReference(req.getReferenceHint());

            Reservation r = payment.getReservation();
            r.setPaid(true);
            r.setStatus(ReservationStatus.CONFIRMED);
            reservationRepository.save(r);

        } else {
            payment.setStatus(Payment.Status.FAILED);
            payment.setPaymentReference(req.getReferenceHint());
        }

        Payment saved = paymentRepository.save(payment);
        return toDTO(saved);
    }

    // --------------------------------------------------
    // 3️⃣ Listar pagos del usuario autenticado
    // --------------------------------------------------

    @Transactional(readOnly = true)
    public List<PaymentDTO> listMyPayments(Long userId) {
        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes consultar pagos de otro usuario.");
        }

        return paymentRepository.findByUser_Id(userId)
                .stream().map(this::toDTO).toList();
    }

    // --------------------------------------------------
    // 4️⃣ Pago off-session con tarjeta guardada
    // --------------------------------------------------

    public PaymentDTO chargeSavedMethod(Long reservationId, Long userId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        ensureCanAccessReservation(r);

        User u = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        ensureCanAccessUser(u);

        Ayuntamiento ay = r.getUser().getAyuntamiento();
        if (ay == null || ay.getStripeAccountId() == null || ay.getStripeAccountId().isBlank()) {
            throw new IllegalStateException("Ayuntamiento sin cuenta de Stripe configurada.");
        }
        String stripeAccountId = ay.getStripeAccountId();

        String customerId = ensureStripeCustomer(u, stripeAccountId);
        String pmId = u.getDefaultPaymentMethodId();

        Payment p = new Payment();
        p.setReservation(r);
        p.setUser(u);
        p.setProvider(Payment.Provider.STRIPE);
        p.setProviderAccountId(stripeAccountId);
        p.setStatus(Payment.Status.PENDING);
        p.setAmount(DEFAULT_PRICE);
        p.setCurrency("EUR");
        paymentRepository.save(p);

        long cents = p.getAmount().movePointRight(2).longValueExact();

        try {
            PaymentIntentCreateParams.Builder b = PaymentIntentCreateParams.builder()
                    .setAmount(cents)
                    .setCurrency("eur")
                    .setCustomer(customerId)
                    .setConfirm(true)
                    .setOffSession(true)
                    .putMetadata("paymentId", String.valueOf(p.getId()))
                    .putMetadata("reservationId", String.valueOf(r.getId()));

            if (pmId != null) {
                b.setPaymentMethod(pmId);
            }

            RequestOptions opts = RequestOptions.builder()
                    .setApiKey(System.getenv(stripeSecretKey))   // 🔥 OBLIGATORIO
                    .setStripeAccount(stripeAccountId)                 // CUENTA CONNECT
                    .build();


            PaymentIntent pi = PaymentIntent.create(b.build(), opts);

            p.setPaymentIntentId(pi.getId());
            paymentRepository.save(p);

            return toDTO(p);

        } catch (Exception e) {
            throw new IllegalStateException("Error procesando pago off-session", e);
        }
    }

    // --------------------------------------------------
    // Helpers Stripe (multi-cuenta)
    // --------------------------------------------------

    /**
     * Cliente de Stripe por ayuntamiento (cuenta Connect).
     */
    private String ensureStripeCustomer(User user, String stripeAccountId) {
        if (user.getStripeCustomerId() != null) return user.getStripeCustomerId();

        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .setName(user.getFullName())
                    .build();

            RequestOptions opts = RequestOptions.builder()
                    .setApiKey(System.getenv(stripeSecretKey))   // 🔥 OBLIGATORIO
                    .setStripeAccount(stripeAccountId)                 // CUENTA CONNECT
                    .build();

            Customer c = Customer.create(params, opts);

            user.setStripeCustomerId(c.getId());
            userRepository.save(user);

            return c.getId();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear cliente en Stripe", e);
        }
    }

    private PaymentIntent createStripeIntent(Payment saved,
                                             String customerId,
                                             CheckoutRequest req,
                                             String stripeAccountId) throws Exception {

        long amountCents = saved.getAmount().movePointRight(2).longValueExact();

        PaymentIntentCreateParams.Builder pi =
                PaymentIntentCreateParams.builder()
                        .setAmount(amountCents)
                        .setCurrency("eur")
                        .setCustomer(customerId)
                        .putMetadata("reservationId", String.valueOf(saved.getReservation().getId()))
                        .putMetadata("paymentId", String.valueOf(saved.getId()))
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods
                                        .builder().setEnabled(true).build()
                        );

        if (Boolean.TRUE.equals(req.getSaveCard())) {
            pi.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION);
        }

        RequestOptions opts = RequestOptions.builder()
                .setApiKey(System.getenv(stripeSecretKey))   // 🔥 OBLIGATORIO
                .setStripeAccount(stripeAccountId)                 // CUENTA CONNECT
                .build();

        System.out.println("⚡ PaymentIntent creado en CONNECT account: " + stripeAccountId);
        return PaymentIntent.create(pi.build(), opts);
    }

    // --------------------------------------------------
    // Helper auto-success (modo test)
    // --------------------------------------------------

    private void handleAutoSuccess(Payment payment, Reservation reservation) {
        payment.setStatus(Payment.Status.SUCCEEDED);

        reservation.setPaid(true);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        reservationRepository.save(reservation);
        paymentRepository.save(payment);

        // Si quieres, aquí puedes reutilizar el envío de email como en los webhooks
    }

    // --------------------------------------------------
    // DTO Converter
    // --------------------------------------------------

    private PaymentDTO toDTO(Payment p) {
        PaymentDTO dto = new PaymentDTO();
        dto.setId(p.getId());
        dto.setReservationId(p.getReservation().getId());
        dto.setUserId(p.getUser().getId());
        dto.setProvider(p.getProvider());
        dto.setStatus(p.getStatus());
        dto.setAmount(p.getAmount());
        dto.setCurrency(p.getCurrency());
        dto.setPaymentReference(p.getPaymentReference());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setPaymentIntentId(p.getPaymentIntentId());
        dto.setProviderAccountId(p.getProviderAccountId());
        dto.setStripeAccountId(p.getProviderAccountId());

        return dto;
    }
}
