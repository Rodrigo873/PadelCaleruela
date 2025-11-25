package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.PistaDTO;
import com.example.PadelCaleruela.service.PistaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pistas")
public class PistaController {

    private final PistaService pistaService;

    @GetMapping
    public List<PistaDTO> getAll() {
        return pistaService.getAllForCurrentUser();
    }

    @PostMapping
    public PistaDTO create(@RequestBody PistaDTO dto) {
        return pistaService.create(dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        pistaService.delete(id);
    }
}
