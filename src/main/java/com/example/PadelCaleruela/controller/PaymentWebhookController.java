package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody String payload) {
        try {
            paymentWebhookService.handleStripeEvent(payload, signature);
            return ResponseEntity.ok("ok");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno: " + e.getMessage());
        }
    }
}
