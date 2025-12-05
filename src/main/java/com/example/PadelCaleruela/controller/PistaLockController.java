package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.LockRegisterDTO;
import com.example.PadelCaleruela.model.Lock;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.repository.LockRepository;
import com.example.PadelCaleruela.service.LockService;
import com.example.PadelCaleruela.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pistas")
public class PistaLockController {

    private final LockRepository repo;

    private final ReservationService reservaService;

    private final LockService lockService;
    public PistaLockController(LockRepository repo,ReservationService reservaService,LockService lockService) {
        this.repo = repo;
        this.reservaService=reservaService;
        this.lockService=lockService;
    }

    @GetMapping("/{id}/pista")
    public ResponseEntity<?> getPistaDeReserva(@PathVariable Long id) {
        Reservation reserva = reservaService.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        if (reserva.getPista() == null) {
            throw new RuntimeException("La reserva no tiene pista asignada");
        }

        return ResponseEntity.ok(Map.of(
                "pistaId", reserva.getPista().getId()
        ));
    }

    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('SUPERADMIN')")
    public ResponseEntity<LockRegisterDTO> registerLock(@RequestBody LockRegisterDTO dto) {

        Lock saved = lockService.registerAndSaveLock(
                dto.getName(),
                dto.getDeviceNumber(),
                dto.getBleName(),
                dto.getDeviceMAC(),
                dto.getPistaId()
        );

        return ResponseEntity.ok(lockService.toDTO(saved));
    }

    @PostMapping("/puerta-abierta/{userId}")
    public ResponseEntity<?> notifyDoorOpen(@PathVariable Long userId) {

        lockService.notifyTownHallDoorWasOpen(userId);

        return ResponseEntity.ok(
                Map.of("status", "ok", "message", "Notificación enviada al ayuntamiento")
        );
    }



}
