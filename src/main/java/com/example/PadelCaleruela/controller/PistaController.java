package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.LockRegisterDTO;
import com.example.PadelCaleruela.dto.PistaDTO;
import com.example.PadelCaleruela.model.Lock;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.UserRepository;
import com.example.PadelCaleruela.service.LockService;
import com.example.PadelCaleruela.service.PistaService;
import com.example.PadelCaleruela.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pistas")
public class PistaController {

    private final PistaService pistaService;

    private final UserRepository userRepo;
    private final LockService lockService;
    private final ReservationService reservaService;

    @GetMapping
    public List<PistaDTO> getAll() {
        return pistaService.getAllForCurrentUser();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN')")
    public PistaDTO create(@RequestBody PistaDTO dto) {
        return pistaService.create(dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        pistaService.delete(id);
    }

    @GetMapping("/{pistaId}/locks")
    @PreAuthorize("hasAnyRole('SUPERADMIN')")
    public ResponseEntity<List<LockRegisterDTO>> getLocksByPista(@PathVariable Long pistaId) {
        System.out.println("🔍 Controller: solicitando cerraduras de pista " + pistaId);
        return ResponseEntity.ok(lockService.getLocksByPista(pistaId));
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PistaDTO> updatePista(@RequestBody PistaDTO dto) {
        return ResponseEntity.ok(pistaService.updatePista(dto));
    }


    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('SUPERADMIN')")
    public ResponseEntity<?> listarPistas() {
        return ResponseEntity.ok(pistaService.listarPistasResumen());
    }


}
