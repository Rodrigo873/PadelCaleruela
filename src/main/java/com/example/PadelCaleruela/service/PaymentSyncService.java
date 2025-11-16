package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.PaymentDTO;
import com.example.PadelCaleruela.model.Payment;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.PaymentRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentSyncService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final EmailService emailService;
    private final AuthService authService;


    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    /**
     * Sincroniza el estado real del PaymentIntent desde Stripe.
     * SOLO SUPERADMIN y ADMIN pueden usar esta operación.
     */
    @RequestMapping(value = "/sync-intent", method = RequestMethod.POST)
    public PaymentDTO syncIntent(@RequestParam String paymentIntentId) {

        Optional<Payment> pay = paymentRepository.findByPaymentIntentId(paymentIntentId);

        Payment p=pay.get();

        if (p == null) {
            throw new IllegalArgumentException("No existe un pago con ese intent.");
        }

        // ⚠️ MUY IMPORTANTE
        String stripeAccountId = p.getProviderAccountId(); // cuenta del ayuntamiento

        try {
            RequestOptions opts = RequestOptions.builder()
                    .setApiKey(System.getenv("STRIPE_SECRET_KEY"))
                    .setStripeAccount(stripeAccountId) // 👈 leer desde la cuenta CONECTADA
                    .build();

            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, opts);

            if ("succeeded".equals(intent.getStatus())) {
                p.setStatus(Payment.Status.SUCCEEDED);
                Reservation r = p.getReservation();
                r.setPaid(true);
                r.setStatus(ReservationStatus.CONFIRMED);
                reservationRepository.save(r);
            }

            paymentRepository.save(p);
            return toDTO(p);

        } catch (Exception e) {
            throw new RuntimeException("Error sincronizando intent en Stripe", e);
        }
    }

    // --------------------------
    // 🔥 ESTADOS DEL PAGO
    // --------------------------

    private void handleSucceeded(Payment p) {
        p.setStatus(Payment.Status.SUCCEEDED);

        Reservation r = p.getReservation();
        r.setPaid(true);
        r.setStatus(ReservationStatus.CONFIRMED);

        reservationRepository.save(r);
        paymentRepository.save(p);

        try {
            sendConfirmationEmails(r);
        } catch (Exception e) {
            System.err.println("⚠ Error enviando email: " + e.getMessage());
        }
    }

    private void handleProcessing(Payment p) {
        p.setStatus(Payment.Status.PENDING);
        paymentRepository.save(p);
    }

    private void handleFailed(Payment p) {
        p.setStatus(Payment.Status.FAILED);
        paymentRepository.save(p);
    }

    // --------------------------
    // 💌 EMAILS
    // --------------------------

    private void sendConfirmationEmails(Reservation r) throws MessagingException {

        Set<User> jugadores = r.getJugadores();
        String creador = r.getUser().getFullName();
        LocalDateTime fechaHora = r.getStartTime();

        String fecha = fechaHora.format(
                DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", new Locale("es","ES"))
        );

        StringBuilder list = new StringBuilder();
        for (User j : jugadores) {
            list.append("<li>").append(j.getFullName()).append("</li>");
        }

        String html = """
            <div style="font-family: Arial; color: #333;">
                <h2 style="color:#0b5ed7;">Pago confirmado 🎾</h2>
                <p>La reserva de <b>%s</b> ha sido confirmada.</p>
                <p><b>Fecha:</b> %s</p>
                <ul>%s</ul>
            </div>
            """.formatted(creador, fecha, list);

        for (User jugador : jugadores) {
            if (jugador.getEmail() != null && !jugador.getEmail().isEmpty()) {
                emailService.sendHtmlEmail(
                        jugador.getEmail(),
                        "Reserva confirmada - " + fecha,
                        html
                );
            }
        }
    }

    // --------------------------
    // DTO
    // --------------------------

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
