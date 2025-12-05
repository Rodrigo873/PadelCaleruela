package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.LockPasswordDTO;
import com.example.PadelCaleruela.service.LockPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lock/password")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class LockPasswordController {

    private final LockPasswordService lockPasswordService;

    /**
     * Obtener todas las contraseñas generadas para una reserva
     */
    @GetMapping("/{reservationId}/lock-passwords")
    public ResponseEntity<List<LockPasswordDTO>> getLockPasswords(
            @PathVariable Long reservationId
    ) {
        List<LockPasswordDTO> list = lockPasswordService.getPasswordsByReservation(reservationId);
        return ResponseEntity.ok(list);
    }
}
