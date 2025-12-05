package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.LockSimpleDTO;
import com.example.PadelCaleruela.service.LockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locks")
public class LockController {

    private final LockService lockService;

    public LockController(LockService lockService) {
        this.lockService = lockService;
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<LockSimpleDTO>> getAllLocksSimple() {
        return ResponseEntity.ok(lockService.getAllLocksSimple());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deleteLock(@PathVariable Long id) {
        lockService.deleteLock(id);
        return ResponseEntity.noContent().build(); // 204 OK sin cuerpo
    }

}
