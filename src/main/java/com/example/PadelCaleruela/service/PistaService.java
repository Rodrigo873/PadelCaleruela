package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.PistaDTO;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Pista;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.AyuntamientoRepository;
import com.example.PadelCaleruela.repository.PistaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PistaService {

    private final PistaRepository pistaRepository;
    private final AyuntamientoRepository ayuntamientoRepository;
    private final AuthService authService; // 🔹 Necesitamos saber quién es el usuario logueado

    // 🔹 SUPERADMIN: todas las pistas
    // 🔹 ADMIN: solo las de SU ayuntamiento
    public List<PistaDTO> getAllForCurrentUser() {
        User user = authService.getCurrentUser();

        boolean esSuper = user.getRole().equals("SUPERADMIN");

        if (esSuper) {
            return pistaRepository.findAll()
                    .stream()
                    .map(this::toDto)
                    .toList();
        }

        Long ayuntamientoId = user.getAyuntamiento().getId();

        return pistaRepository.findByAyuntamientoId(ayuntamientoId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // Crear pista con seguridad
    public PistaDTO create(PistaDTO dto) {

        User user = authService.getCurrentUser();
        boolean esSuper = user.getRole().equals("SUPERADMIN");

        Long ayuntamientoId;

        if (esSuper) {
            // SuperAdmin puede elegir cualquier ayuntamiento
            ayuntamientoId = dto.getAyuntamientoId();
        } else {
            // Admin: se ignora lo que mande el front
            ayuntamientoId = user.getAyuntamiento().getId();
        }

        Ayuntamiento ay = ayuntamientoRepository.findById(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        Pista pista = new Pista();
        pista.setAyuntamiento(ay);
        pista.setNombre(dto.getNombre());
        pista.setActiva(dto.isActiva());

        Pista saved = pistaRepository.save(pista);
        return toDto(saved);
    }

    // Seguridad en borrado:
    public void delete(Long id) {

        User user = authService.getCurrentUser();
        boolean esSuper = user.getRole().equals("SUPERADMIN");

        Pista pista = pistaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pista no encontrada"));

        if (!esSuper) {
            if (!pista.getAyuntamiento().getId().equals(user.getAyuntamiento().getId())) {
                throw new RuntimeException("No tienes permiso para eliminar esta pista");
            }
        }

        pistaRepository.deleteById(id);
    }

    private PistaDTO toDto(Pista pista) {
        PistaDTO dto = new PistaDTO();
        dto.setId(pista.getId());
        dto.setNombre(pista.getNombre());
        dto.setActiva(pista.isActiva());
        dto.setAyuntamientoId(pista.getAyuntamiento().getId());
        return dto;
    }
}
