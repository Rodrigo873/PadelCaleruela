package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.WelockClient;
import com.example.PadelCaleruela.dto.PaymentDTO;
import com.example.PadelCaleruela.model.Payment;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.PaymentRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentSyncService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final EmailService emailService;
    private final AuthService authService;
    private final WelockClient welockClient;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    /**
     * Devuelve el estado actual del pago en tu BD a partir del paymentIntentId.
     * NO llama a Stripe, NO genera Welock, NO envía emails.
     * El webhook es el que actualiza la BD.
     */
    public PaymentDTO syncIntent(String paymentIntentId) {
        System.out.println("🟡 [SYNC] syncIntent llamado con paymentIntentId = " + paymentIntentId);

        Optional<Payment> pay = paymentRepository.findByPaymentIntentId(paymentIntentId);

        Payment p = pay.orElseThrow(() ->
                new IllegalArgumentException("No existe un pago con ese intent.")
        );
        System.out.println("🟡 [SYNC] Pago encontrado en BD: id " + p.getId() + " status=" + p.getStatus());
        System.out.println("🟡 [SYNC] providerAccountId=" + p.getProviderAccountId());

        // Simplemente devolvemos el DTO con el estado actual en BD
        return toDTO(p);
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
        dto.setPaymentIntentId(p.getPaymentIntentId());
        dto.setProviderAccountId(p.getProviderAccountId());
        dto.setStripeAccountId(p.getProviderAccountId());
        return dto;
    }
}
