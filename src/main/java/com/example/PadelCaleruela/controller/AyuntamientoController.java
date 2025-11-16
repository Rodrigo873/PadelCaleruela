package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.AyuntamientoCreateRequest;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.service.AyuntamientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ayuntamientos")
@RequiredArgsConstructor
public class AyuntamientoController {

    private final AyuntamientoService service;

    @GetMapping("/cp/{cp}")
    public ResponseEntity<Ayuntamiento> getByCP(@PathVariable String cp) {
        return ResponseEntity.ok(service.findByCodigoPostal(cp));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Ayuntamiento> crear(
            @Valid @RequestBody AyuntamientoCreateRequest request
    ) {
        return ResponseEntity.ok(service.crearAyuntamiento(request));
    }
}

