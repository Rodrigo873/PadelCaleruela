// PaymentService.java
package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.PaymentDTO;
import com.example.PadelCaleruela.dto.CheckoutRequest;
import com.example.PadelCaleruela.dto.ConfirmPaymentRequest;
import com.example.PadelCaleruela.model.Payment;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.PaymentRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("10.00"); // 1,5h = 10€

    // 1) Crear "checkout" (simulado): genera Payment PENDING
    private String ensureStripeCustomer(User user) {
        if (user.getStripeCustomerId() != null) return user.getStripeCustomerId();
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .setName(user.getFullName()) // si lo tienes
                    .build();
            Customer c = Customer.create(params);
            user.setStripeCustomerId(c.getId());
            userRepository.save(user);
            return c.getId();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear cliente en Stripe", e);
        }
    }

    public PaymentDTO createCheckout(CheckoutRequest req) {
        Reservation reservation = reservationRepository.findById(req.getReservationId())
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        if (reservation.isPaid()) {
            throw new IllegalStateException("La reserva ya está pagada.");
        }

        if (!reservation.getUser().getId().equals(req.getUserId())) {
            throw new IllegalStateException("No puedes pagar una reserva de otro usuario.");
        }

        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // ✅ 1) Reutilizar pago pendiente si ya existe
        Payment existing = paymentRepository.findByReservation_Id(reservation.getId());
        if (existing != null) {
            if (existing.getStatus() == Payment.Status.SUCCEEDED) {
                throw new IllegalStateException("Esta reserva ya fue pagada con éxito.");
            }

            // Reutiliza el PaymentIntent si existe
            PaymentDTO dto = toDTO(existing);
            dto.setClientSecret(existing.getPaymentReference());
            dto.setPaymentIntentId(existing.getPaymentIntentId());
            return dto;
        }

        // ✅ 2) Asegura Customer en Stripe
        String customerId = ensureStripeCustomer(user);

        // ✅ 3) Crear Payment entidad local (PENDING)
        Payment p = new Payment();
        p.setReservation(reservation);
        p.setUser(user);
        p.setProvider(Payment.Provider.STRIPE);
        p.setStatus(Payment.Status.PENDING);
        p.setAmount(req.getAmount() == null ? DEFAULT_PRICE : req.getAmount());
        p.setCurrency("EUR");
        Payment saved = paymentRepository.save(p);

        // ✅ 4) Crear PaymentIntent en Stripe
        long amountCents = saved.getAmount().movePointRight(2).longValueExact();

        PaymentIntentCreateParams.Builder pi = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("eur")
                .setCustomer(customerId)
                .putMetadata("reservationId", String.valueOf(reservation.getId()))
                .putMetadata("paymentId", String.valueOf(saved.getId()))
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true).build()
                );

        if (Boolean.TRUE.equals(req.getSaveCard())) {
            pi.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION);
        }

        try {
            PaymentIntent intent = PaymentIntent.create(pi.build());
            saved.setPaymentIntentId(intent.getId());
            saved.setPaymentReference(intent.getClientSecret());
            paymentRepository.save(saved);

            // ✅ Si ya viene confirmado por Stripe (por ejemplo, en pruebas sin 3DS)
            if ("succeeded".equalsIgnoreCase(intent.getStatus())) {
                saved.setStatus(Payment.Status.SUCCEEDED);
                reservation.setPaid(true);
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservationRepository.save(reservation);
                paymentRepository.save(saved);

                // 💌 Enviar email de confirmación de pago a todos los jugadores
                try {
                    Set<User> jugadores = reservation.getJugadores(); // o new ArrayList<>(...) si es Set
                    String creador = reservation.getUser().getFullName();
                    LocalDateTime fechaHora = reservation.getStartTime();

                    String fechaFormateada = fechaHora.format(
                            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", new Locale("es", "ES"))
                    );

                    StringBuilder jugadoresList = new StringBuilder();
                    for (User jugador : jugadores) {
                        jugadoresList.append("<li>").append(jugador.getFullName()).append("</li>");
                    }

                    String html = """
        <div style="font-family: Arial, sans-serif; color: #333;">
            <h2 style="color:#0b5ed7;">Pago confirmado 🎾</h2>
            <p>Hola,</p>
            <p>La reserva creada por <b>%s</b> ha sido <strong>confirmada y pagada</strong>.</p>
            <p><strong>Fecha y hora:</strong> %s</p>
            <p><strong>Jugadores:</strong></p>
            <ul>%s</ul>
            <p>¡Nos vemos en la pista! 🏸</p>
            <hr>
            <p style="font-size:0.9rem; color:#555;">Club de Pádel Caleruela</p>
        </div>
        """.formatted(creador, fechaFormateada, jugadoresList);

                    for (User jugador : jugadores) {
                        if (jugador.getEmail() != null && !jugador.getEmail().isEmpty()) {
                            emailService.sendHtmlEmail(
                                    jugador.getEmail(),
                                    "Reserva confirmada y pagada - " + fechaFormateada,
                                    html
                            );
                        }
                    }
                } catch (MessagingException e) {
                    System.err.println("⚠️ Error al enviar correo de confirmación de pago: " + e.getMessage());
                }
            }


            PaymentDTO dto = toDTO(saved);
            dto.setClientSecret(intent.getClientSecret());
            dto.setPaymentIntentId(intent.getId());
            return dto;

        } catch (Exception e) {
            throw new RuntimeException("Error creando PaymentIntent", e);
        }
    }



    // 2) Confirmar pago (simulado): marca success o failed y actualiza Reservation.paid
    @Transactional
    public PaymentDTO confirmPayment(ConfirmPaymentRequest req) {
        Payment payment = paymentRepository.findById(req.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));

        if (payment.getStatus() != Payment.Status.PENDING) {
            throw new IllegalStateException("El pago ya fue procesado.");
        }

        if (req.isSuccess()) {
            payment.setStatus(Payment.Status.SUCCEEDED);
            payment.setPaymentReference(req.getReferenceHint() != null ?
                    req.getReferenceHint() : "OK_" + payment.getId());
            // marca la reserva como pagada
            Reservation r = payment.getReservation();
            r.setPaid(true);
        } else {
            payment.setStatus(Payment.Status.FAILED);
            payment.setPaymentReference(req.getReferenceHint() != null ?
                    req.getReferenceHint() : "FAIL_" + payment.getId());
        }

        if (req.isSuccess()) {
            payment.setStatus(Payment.Status.SUCCEEDED);
            Reservation r = payment.getReservation();
            r.setPaid(true);
            r.setStatus(ReservationStatus.CONFIRMED);
            //En pruebas esto de abajo
            reservationRepository.save(r);
        }


        Payment saved = paymentRepository.save(payment);
        return toDTO(saved);
    }

    // 3) Listar pagos del usuario
    @Transactional(readOnly = true)
    public List<PaymentDTO> listMyPayments(Long userId) {
        return paymentRepository.findByUser_Id(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    // PaymentService.java (pago rápido reutilizando PM)
    public PaymentDTO chargeSavedMethod(Long reservationId, Long userId) {
        Reservation r = reservationRepository.findById(reservationId).orElseThrow();
        User u = userRepository.findById(userId).orElseThrow();

        String customerId = ensureStripeCustomer(u);
        String pmId = u.getDefaultPaymentMethodId(); // obtén el último guardado o deja que Stripe elija

        Payment p = new Payment();
        p.setReservation(r);
        p.setUser(u);
        p.setProvider(Payment.Provider.STRIPE);
        p.setStatus(Payment.Status.PENDING);
        p.setAmount(DEFAULT_PRICE);
        p.setCurrency("EUR");
        paymentRepository.save(p);

        long amountCents = p.getAmount().movePointRight(2).longValueExact();

        PaymentIntentCreateParams.Builder b = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("eur")
                .setCustomer(customerId)
                .setConfirm(true)               // confirmarlo ya
                .setOffSession(true)            // sin UI
                .putMetadata("paymentId", String.valueOf(p.getId()))
                .putMetadata("reservationId", String.valueOf(r.getId()));

        if (pmId != null) b.setPaymentMethod(pmId);

        try {
            PaymentIntent pi = PaymentIntent.create(b.build());
            p.setPaymentIntentId(pi.getId());
            paymentRepository.save(p);
            // éxito/fallo lo resolverá el webhook igualmente
            return toDTO(p);
        } catch (CardException ce) {
            // podría requerir acción (SCA) -> vuelve a UI
            throw new IllegalStateException("Se requiere autenticación del banco", ce);
        } catch (Exception e) {
            throw new RuntimeException("Error creando cargo off-session", e);
        }
    }


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
        return dto;
    }
}
