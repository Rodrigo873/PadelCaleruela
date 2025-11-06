package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.PaymentDTO;
import com.example.PadelCaleruela.model.Payment;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.PaymentRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.stripe.model.PaymentIntent;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentSyncService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final EmailService emailService;

    public PaymentDTO syncIntent(String paymentIntentId) throws Exception {
        // 1️⃣ Leer PaymentIntent de Stripe
        PaymentIntent pi = PaymentIntent.retrieve(paymentIntentId);
        if (pi == null) throw new IllegalArgumentException("PaymentIntent no encontrado en Stripe");

        // 2️⃣ Buscar Payment interno por metadata
        String paymentIdStr = pi.getMetadata() != null ? pi.getMetadata().get("paymentId") : null;
        if (paymentIdStr == null) throw new IllegalArgumentException("No se encontró 'paymentId' en metadata");

        Payment payment = paymentRepository.findById(Long.valueOf(paymentIdStr))
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado en la base de datos"));

        // 3️⃣ Procesar según estado de Stripe
        switch (pi.getStatus()) {
            case "succeeded" -> handleSucceeded(payment);
            case "processing" -> handleProcessing(payment);
            case "requires_payment_method", "requires_action", "canceled" -> handleFailed(payment);
            default -> {
                // No hacer nada
            }
        }

        // 4️⃣ Devolver DTO
        return toDTO(payment);
    }

    // 🟢 Pago exitoso
    private void handleSucceeded(Payment p) {
        p.setStatus(Payment.Status.SUCCEEDED);
        Reservation r = p.getReservation();
        r.setPaid(true);
        r.setStatus(ReservationStatus.CONFIRMED);

        reservationRepository.save(r);
        paymentRepository.save(p);

        // Enviar correos (asíncronos con @Async)
        try {
            sendConfirmationEmails(r);
        } catch (Exception e) {
            System.err.println("⚠️ Error al enviar email de confirmación: " + e.getMessage());
        }
    }

    // 🕓 Pago en procesamiento
    private void handleProcessing(Payment p) {
        p.setStatus(Payment.Status.PENDING);
        paymentRepository.save(p);
    }

    // ❌ Pago fallido o cancelado
    private void handleFailed(Payment p) {
        p.setStatus(Payment.Status.FAILED);
        paymentRepository.save(p);
    }

    // 💌 Envío de emails (con formateo bonito)
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

    // 🧩 Mapeo a DTO
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
