package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.TarifaDTO;
import com.example.PadelCaleruela.dto.TarifaFranjaDTO;
import com.example.PadelCaleruela.service.TarifaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ayuntamientos/{ayuntamientoId}/tarifas")
@RequiredArgsConstructor
public class TarifaController {

    private final TarifaService tarifaService;

    // ================= TARIFA BASE =================

    @GetMapping
    public ResponseEntity<TarifaDTO> obtenerTarifa(
            @PathVariable Long ayuntamientoId
    ) {
        return ResponseEntity.ok(tarifaService.getTarifa(ayuntamientoId));
    }

    @PostMapping
    public ResponseEntity<TarifaDTO> crearTarifa(
            @PathVariable Long ayuntamientoId,
            @RequestBody TarifaDTO dto
    ) {
        return ResponseEntity.ok(tarifaService.crearTarifa(ayuntamientoId, dto));
    }

    @PutMapping
    public ResponseEntity<TarifaDTO> actualizarTarifa(
            @PathVariable Long ayuntamientoId,
            @RequestBody TarifaDTO dto
    ) {
        return ResponseEntity.ok(tarifaService.actualizarTarifa(ayuntamientoId, dto));
    }

    // ================= FRANJAS =================

    @GetMapping("/franjas")
    public List<TarifaFranjaDTO> listarFranjas(
            @PathVariable Long ayuntamientoId
    ) {
        return tarifaService.listarFranjas(ayuntamientoId);
    }

    @PostMapping("/franjas")
    public TarifaFranjaDTO crearFranja(
            @PathVariable Long ayuntamientoId,
            @RequestBody TarifaFranjaDTO dto
    ) {
        return tarifaService.crearFranja(ayuntamientoId, dto);
    }

    @PutMapping("/franjas/{franjaId}")
    public TarifaFranjaDTO actualizarFranja(
            @PathVariable Long ayuntamientoId,
            @PathVariable Long franjaId,
            @RequestBody TarifaFranjaDTO dto
    ) {
        return tarifaService.actualizarFranja(ayuntamientoId, franjaId, dto);
    }

    @DeleteMapping("/franjas/{franjaId}")
    public void eliminarFranja(
            @PathVariable Long ayuntamientoId,
            @PathVariable Long franjaId
    ) {
        tarifaService.eliminarFranja(ayuntamientoId, franjaId);
    }
}
