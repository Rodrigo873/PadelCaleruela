package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.service.PistaLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pistas")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class PistaLookupController {

    private final PistaLookupService pistaLookupService;

    @GetMapping("/by-reserva/{reservaId}")
    public ResponseEntity<?> getPistaId(
            @PathVariable Long reservaId,
            @RequestHeader("userId") Long userId) {

        try {
            Long pistaId = pistaLookupService.getPistaIdFromReservation(reservaId, userId);
            return ResponseEntity.ok(pistaId); // <-- SOLO el ID
        } catch (Exception e) {
            return ResponseEntity.status(403).body(
                    java.util.Map.of("error", e.getMessage())
            );
        }
    }


}
