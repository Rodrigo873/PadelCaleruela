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
import lombok.RequiredArgsConstructor;
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

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("10.00"); // 1,5h = 10€

    // 1) Crear "checkout" (simulado): genera Payment PENDING
    public PaymentDTO createCheckout(CheckoutRequest req) {
        Reservation reservation = reservationRepository.findById(req.getReservationId())
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        if (reservation.isPaid()) {
            throw new IllegalStateException("La reserva ya está pagada.");
        }

        if (!reservation.getUser().getId().equals(req.getUserId())) {
            throw new IllegalStateException("No puedes pagar una reserva de otro usuario.");
        }

        if (paymentRepository.existsByReservation_Id(reservation.getId())) {
            throw new IllegalStateException("Ya existe un pago asociado a esta reserva.");
        }

        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Payment p = new Payment();
        p.setReservation(reservation);
        p.setUser(user);
        p.setProvider(req.getProvider() == null ? Payment.Provider.FAKE : req.getProvider());
        p.setStatus(Payment.Status.PENDING);
        p.setAmount(req.getAmount() == null ? DEFAULT_PRICE : req.getAmount());
        p.setCurrency("EUR");
        // En integración real: aquí llamarías a Stripe/PayPal para obtener clientSecret / orderId
        p.setPaymentReference("CHK_" + reservation.getId()); // simulado

        Payment saved = paymentRepository.save(p);
        return toDTO(saved);
    }

    // 2) Confirmar pago (simulado): marca success o failed y actualiza Reservation.paid
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
