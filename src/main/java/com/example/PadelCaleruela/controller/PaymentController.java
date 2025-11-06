// PaymentController.java
package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.PaymentDTO;
import com.example.PadelCaleruela.dto.CheckoutRequest;
import com.example.PadelCaleruela.dto.ConfirmPaymentRequest;
import com.example.PadelCaleruela.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 1) Crear “checkout”
    @PostMapping("/checkout")
    public ResponseEntity<PaymentDTO> createCheckout(@RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(paymentService.createCheckout(request));
    }

    /*
    // 2) Confirmar resultado (simulado)
    @PostMapping("/confirm")
    public ResponseEntity<PaymentDTO> confirm(@RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.ok(paymentService.confirmPayment(request));
    }
    */

    // 3) Listar mis pagos
    @GetMapping("/mine/{userId}")
    public ResponseEntity<List<PaymentDTO>> myPayments(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.listMyPayments(userId));
    }
}

