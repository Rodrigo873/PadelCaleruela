package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.PaymentDTO;
import com.example.PadelCaleruela.service.PaymentSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentSyncController {

    private final PaymentSyncService paymentSyncService;

    @PostMapping("/sync-intent")
    public ResponseEntity<PaymentDTO> syncIntent(@RequestParam String paymentIntentId) {
        try {
            PaymentDTO dto = paymentSyncService.syncIntent(paymentIntentId);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
}
