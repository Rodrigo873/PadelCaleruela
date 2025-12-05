package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.PistaDTO;
import com.example.PadelCaleruela.dto.PistaListDTO;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Pista;
import com.example.PadelCaleruela.model.Role;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.AyuntamientoRepository;
import com.example.PadelCaleruela.repository.PistaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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

        boolean esSuper = user.getRole().equals(Role.SUPERADMIN);

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

    @Transactional
    public PistaDTO updatePista(PistaDTO dto) {
        // 1. Buscar pista
        Pista pista = pistaRepository.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("Pista no encontrada"));

        // 2. Seguridad: obtener usuario actual
        User current = authService.getCurrentUser();

        // 3. Validaciones de permisos
        if (!authService.isSuperAdmin()) {
            if (!authService.isAdmin()) {
                throw new AccessDeniedException("No tienes permiso para actualizar pistas.");
            }

            // ADMIN → solo si la pista es de su ayuntamiento
            Long userAyto = current.getAyuntamiento().getId();
            Long pistaAyto = pista.getAyuntamiento().getId();

            if (!userAyto.equals(pistaAyto)) {
                throw new AccessDeniedException("No puedes actualizar pistas de otro ayuntamiento.");
            }
        }

        // 4. Validación lógica: solo se pueden modificar nombre y activa
        pista.setNombre(dto.getNombre());
        pista.setActiva(dto.isActiva());
        pista.setApertura(LocalTime.parse(dto.getHoraApertura()));
        pista.setCierre(LocalTime.parse(dto.getHoraCierre()));

        // 5. Guardar cambios
        pistaRepository.save(pista);

        // 6. Devolver DTO actualizado
        return new PistaDTO(
                pista.getId(),
                pista.getAyuntamiento().getId(),
                pista.getNombre(),
                pista.isActiva(),
                pista.getApertura().toString(),
                pista.getCierre().toString()
        );
    }


    // Crear pista con seguridad
    @Transactional
    public PistaDTO create(PistaDTO dto) {

        User user = authService.getCurrentUser();
        System.out.println(user);
        boolean esSuper = user.getRole().equals(Role.SUPERADMIN);

        Long ayuntamientoId;

        if (esSuper) {
            // SuperAdmin puede elegir cualquier ayuntamiento
            ayuntamientoId = dto.getAyuntamientoId();
        } else {
            throw new RuntimeException("Solo un superadmin puede crear una pista");
        }

        Ayuntamiento ay = ayuntamientoRepository.findById(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        Pista pista = new Pista();
        pista.setAyuntamiento(ay);
        pista.setNombre(dto.getNombre());
        pista.setActiva(dto.isActiva());
        // 5. Actualizar horarios (nuevo)
        if (dto.getHoraApertura() != null) {
            pista.setApertura(LocalTime.parse(dto.getHoraApertura()));
        }

        if (dto.getHoraCierre() != null) {
            pista.setCierre(LocalTime.parse(dto.getHoraCierre()));
        }
        Pista saved = pistaRepository.save(pista);
        Integer pistas=ay.getNumeroPistas()+1;
        ay.setNumeroPistas(pistas);
        ayuntamientoRepository.save(ay);

        return toDto(saved);
    }

    // Seguridad en borrado:
    @Transactional
    public void delete(Long id) {

        User user = authService.getCurrentUser();
        boolean esSuper = user.getRole().equals(Role.SUPERADMIN);

        Pista pista = pistaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pista no encontrada"));

        if (!esSuper) {
            if (!pista.getAyuntamiento().getId().equals(user.getAyuntamiento().getId())) {
                throw new RuntimeException("No tienes permiso para eliminar esta pista");
            }
        }

        pistaRepository.deleteById(id);
        Optional<Ayuntamiento> ayOpt=ayuntamientoRepository.findById(pista.getAyuntamiento().getId());
        Ayuntamiento ay=ayOpt.get();
        Integer pistas=ay.getNumeroPistas()-1;
        ay.setNumeroPistas(pistas);
        ayuntamientoRepository.save(ay);
    }

    public List<PistaListDTO> listarPistasResumen() {
        return pistaRepository.listarResumen();
    }


    private PistaDTO toDto(Pista pista) {
        PistaDTO dto = new PistaDTO();
        dto.setId(pista.getId());
        dto.setNombre(pista.getNombre());
        dto.setActiva(pista.isActiva());
        dto.setAyuntamientoId(pista.getAyuntamiento().getId());
        dto.setHoraApertura(pista.getApertura().toString());
        dto.setHoraCierre(pista.getCierre().toString());
        return dto;
    }
}
