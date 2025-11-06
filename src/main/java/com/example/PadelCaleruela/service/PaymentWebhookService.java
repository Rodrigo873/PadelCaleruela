package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.Payment;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.PaymentRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.stripe.model.*;
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
    private final EmailService emailService;

    public void handleStripeEvent(String payload, String signature) {
        String endpointSecret = System.getenv("STRIPE_WEBHOOK_SECRET");

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, endpointSecret);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Stripe signature: " + e.getMessage());
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (pi != null) onPaymentSucceeded(pi);
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (pi != null) onPaymentFailed(pi);
            }
            default -> {
                System.out.println("⚠️ Evento Stripe ignorado: " + event.getType());
            }
        }
    }

    @Transactional
    protected void onPaymentSucceeded(PaymentIntent pi) {
        String paymentIdStr = pi.getMetadata().get("paymentId");
        if (paymentIdStr == null) return;

        Payment p = paymentRepository.findById(Long.valueOf(paymentIdStr)).orElse(null);
        if (p == null) return;

        try {
            // 💳 Información adicional del método de pago
            String pmId = pi.getPaymentMethod();
            if (pmId != null) {
                PaymentMethod pm = PaymentMethod.retrieve(pmId);
                p.setPaymentMethodId(pmId);
                if (pm.getCard() != null) {
                    p.setCardBrand(pm.getCard().getBrand());
                    p.setCardLast4(pm.getCard().getLast4());
                }
            }

            // 📃 URL del recibo
            ChargeCollection charges = Charge.list(Map.of("payment_intent", pi.getId()));
            if (charges != null && !charges.getData().isEmpty()) {
                Charge ch = charges.getData().get(0);
                p.setProviderReceiptUrl(ch.getReceiptUrl());
            }

        } catch (Exception ignored) {}

        p.setStatus(Payment.Status.SUCCEEDED);

        Reservation r = p.getReservation();
        r.setPaid(true);
        r.setStatus(ReservationStatus.CONFIRMED);

        reservationRepository.save(r);
        paymentRepository.save(p);

        try {
            sendConfirmationEmails(r);
        } catch (MessagingException e) {
            System.err.println("⚠️ Error enviando correo de confirmación: " + e.getMessage());
        }
    }

    @Transactional
    protected void onPaymentFailed(PaymentIntent pi) {
        String paymentIdStr = pi.getMetadata().get("paymentId");
        if (paymentIdStr == null) return;

        Payment p = paymentRepository.findById(Long.valueOf(paymentIdStr)).orElse(null);
        if (p == null) return;

        p.setStatus(Payment.Status.FAILED);
        paymentRepository.save(p);
    }

    private void sendConfirmationEmails(Reservation r) throws MessagingException {
        Set<User> jugadores = r.getJugadores();
        String creador = r.getUser().getFullName();
        LocalDateTime fechaHora = r.getStartTime();

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
    }
}
