package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.*;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.TarifaFranja;
import com.example.PadelCaleruela.service.AyuntamientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ayuntamientos")
@RequiredArgsConstructor
public class AyuntamientoController {

    private final AyuntamientoService service;

    @GetMapping("/cp/{cp}")
    public ResponseEntity<?> getByCP(@PathVariable String cp) {
        return ResponseEntity.ok(service.findByCodigoPostalToDTO(cp));
    }

    @PostMapping(value = "", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> crearAyuntamiento(
            @RequestPart("data") AyuntamientoCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(service.crearAyuntamiento(request, image));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> actualizarAyuntamiento(
            @PathVariable Long id,
            @RequestPart("data") ActualizarAytoYTarifaDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(service.actualizarAyuntamiento(id, dto, image));
    }

    @GetMapping("/simple")
    public ResponseEntity<?> getAyuntamientosSimple() {
        return ResponseEntity.ok(service.getAyuntamientosSimple());
    }



    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> getAyuntamientoById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAyuntamientoById(id));
    }


    @GetMapping
    public ResponseEntity<?> listarAyuntamientos() {
        return ResponseEntity.ok(service.listarAyuntamientos());
    }

    @GetMapping("/logo/{userId}")
    public ResponseEntity<?> getLogoByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getLogoByUserId(userId));
    }

    @PostMapping("/{id}/upload-image")
    public ResponseEntity<?> uploadAyuntamientoImage(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image
    ) throws IOException {
        String imageUrl = service.uploadImage(id, image);
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }
}

