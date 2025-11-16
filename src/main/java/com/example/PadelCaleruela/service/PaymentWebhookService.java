package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.Payment;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.PaymentRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    /**
     * ⚠️ Este endpoint NO debe requerir autenticación de usuario.
     * SOLO valida:
     *   - Firma de Stripe
     *   - Cuenta Connect (stripeAccount)
     */
    public void handleStripeEvent(String payload, String signature) {

        String endpointSecret = System.getenv("STRIPE_WEBHOOK_SECRET");
        if (endpointSecret == null) {
            throw new IllegalStateException("⚠️ STRIPE_WEBHOOK_SECRET no configurado.");
        }

        Event event;

        try {
            event = Webhook.constructEvent(payload, signature, endpointSecret);
        } catch (Exception e) {
            throw new IllegalArgumentException("❌ Firma Stripe NO válida: " + e.getMessage());
        }

        // 🟦 Multi-tenant: Stripe indica qué cuenta Connect originó el evento
        String stripeAccount = event.getAccount();
        if (stripeAccount == null) {
            System.err.println("⚠️ Webhook recibido sin cuenta Stripe asociada. SE IGNORA.");
            return;
        }

        System.out.println("📩 Webhook " + event.getType() + " desde cuenta: " + stripeAccount);

        switch (event.getType()) {

            case "payment_intent.succeeded" -> {
                PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);

                if (pi != null) safeOnPaymentSucceeded(pi, stripeAccount);
            }

            case "payment_intent.payment_failed" -> {
                PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);

                if (pi != null) safeOnPaymentFailed(pi, stripeAccount);
            }

            default -> System.out.println("ℹ️ Evento ignorado por no estar implementado: " + event.getType());
        }
    }

    // =====================================================================================
    // 🟩 PAGO EXITOSO
    // =====================================================================================
    @Transactional
    protected void safeOnPaymentSucceeded(PaymentIntent pi, String stripeAccount) {

        String paymentIdStr = getMeta(pi, "paymentId");
        if (paymentIdStr == null) {
            System.err.println("❌ Webhook sin paymentId. SE IGNORA.");
            return;
        }

        Payment p = paymentRepository.findById(Long.valueOf(paymentIdStr)).orElse(null);
        if (p == null) {
            System.err.println("❌ Pago no encontrado en BD. SE IGNORA.");
            return;
        }

        // evitar reprocesar duplicados
        if (p.getStatus() == Payment.Status.SUCCEEDED) {
            System.out.println("ℹ️ Webhook duplicado, pago ya procesado.");
            return;
        }

        Reservation r = p.getReservation();
        if (r == null) {
            System.err.println("❌ No existe reserva asociada. SE IGNORA.");
            return;
        }

        // =========================================================
        // 🔎 Recuperar PaymentMethod desde la cuenta Connect correcta
        // =========================================================
        try {
            if (pi.getPaymentMethod() != null) {
                RequestOptions opts = RequestOptions.builder()
                        .setStripeAccount(stripeAccount)
                        .build();

                PaymentMethod pm = PaymentMethod.retrieve(pi.getPaymentMethod(), opts);

                p.setPaymentMethodId(pm.getId());

                if (pm.getCard() != null) {
                    p.setCardBrand(pm.getCard().getBrand());
                    p.setCardLast4(pm.getCard().getLast4());
                }
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Error recuperando PaymentMethod: " + ex.getMessage());
        }

        // =========================================================
        // 🔎 Recuperar receipt_url (solo desde cuenta correcta)
        // =========================================================
        try {
            RequestOptions opts = RequestOptions.builder()
                    .setStripeAccount(stripeAccount)
                    .build();

            ChargeCollection charges = Charge.list(
                    Map.of("payment_intent", pi.getId()),
                    opts
            );

            if (charges != null && !charges.getData().isEmpty()) {
                Charge ch = charges.getData().get(0);
                p.setProviderReceiptUrl(ch.getReceiptUrl());
            }

        } catch (Exception ex) {
            System.err.println("⚠️ Error recuperando charge: " + ex.getMessage());
        }

        // =========================================================
        // 🟩 Marcar pago/reserva
        // =========================================================
        p.setStatus(Payment.Status.SUCCEEDED);

        r.setPaid(true);
        r.setStatus(ReservationStatus.CONFIRMED);

        paymentRepository.save(p);
        reservationRepository.save(r);

        try {
            sendConfirmationEmails(r);
        } catch (MessagingException e) {
            System.err.println("⚠️ Error enviando email: " + e.getMessage());
        }

        System.out.println("✅ Pago procesado correctamente en multi-tenant.");
    }


    // =====================================================================================
    // ❌ PAGO FALLIDO
    // =====================================================================================
    @Transactional
    protected void safeOnPaymentFailed(PaymentIntent pi, String stripeAccount) {

        String paymentIdStr = getMeta(pi, "paymentId");
        if (paymentIdStr == null) return;

        Payment p = paymentRepository.findById(Long.valueOf(paymentIdStr)).orElse(null);
        if (p == null) return;

        p.setStatus(Payment.Status.FAILED);
        paymentRepository.save(p);

        System.out.println("❌ Pago fallido procesado via webhook.");
    }

    // =====================================================================================
    // 📩 EMAILS
    // =====================================================================================
    private void sendConfirmationEmails(Reservation r) throws MessagingException {

        Set<User> jugadores = r.getJugadores();
        String creador = r.getUser().getFullName();
        LocalDateTime fechaHora = r.getStartTime();

        String fechaFormateada = fechaHora.format(
                DateTimeFormatter.ofPattern(
                        "EEEE d 'de' MMMM 'a las' HH:mm",
                        new Locale("es", "ES")
                )
        );

        StringBuilder jugadoresList = new StringBuilder();
        jugadores.forEach(j ->
                jugadoresList.append("<li>").append(j.getFullName()).append("</li>")
        );

        String html = """
            <div style="font-family: Arial; color: #333;">
                <h2 style="color:#0b5ed7;">Pago confirmado 🎾</h2>
                <p>La reserva creada por <b>%s</b> ha sido <strong>confirmada y pagada</strong>.</p>
                <p><b>Fecha:</b> %s</p>
                <ul>%s</ul>
            </div>
        """.formatted(creador, fechaFormateada, jugadoresList);

        for (User jugador : jugadores) {
            if (jugador.getEmail() != null && !jugador.getEmail().isEmpty()) {
                emailService.sendHtmlEmail(
                        jugador.getEmail(),
                        "Reserva confirmada - " + fechaFormateada,
                        html
                );
            }
        }
    }

    // =====================================================================================
    // 🔧 Helpers
    // =====================================================================================
    private String getMeta(PaymentIntent pi, String key) {
        return pi.getMetadata() != null ? pi.getMetadata().get(key) : null;
    }
}
