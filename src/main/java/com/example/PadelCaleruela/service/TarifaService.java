package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.TarifaDTO;
import com.example.PadelCaleruela.dto.TarifaFranjaDTO;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Tarifa;
import com.example.PadelCaleruela.model.TarifaFranja;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.Role;
import com.example.PadelCaleruela.repository.AyuntamientoRepository;
import com.example.PadelCaleruela.repository.TarifaFranjaRepository;
import com.example.PadelCaleruela.repository.TarifaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TarifaService {

    private final AyuntamientoRepository aytoRepo;
    private final TarifaRepository tarifaRepo;
    private final TarifaFranjaRepository franjaRepo;

    // ================= VALIDACIÓN ================

    private Ayuntamiento validatePermissions(Long ayuntamientoId) {

        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        // Usuario SUPERADMIN o ADMIN
        if (principal instanceof com.example.PadelCaleruela.CustomUserDetails cud) {

            User currentUser = cud.getUser();

            Ayuntamiento a = aytoRepo.findById(ayuntamientoId)
                    .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

            if (currentUser.getRole() == Role.ADMIN &&
                    !currentUser.getAyuntamiento().getId().equals(ayuntamientoId)) {

                throw new SecurityException("No tienes permisos para modificar este ayuntamiento");
            }

            return a;
        }

        throw new SecurityException("Usuario no autenticado correctamente");
    }


    // ================= TARIFA BASE ================

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public TarifaDTO getTarifa(Long ayuntamientoId) {
        Tarifa t = tarifaRepo.findByAyuntamientoId(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));
        return mapToDTO(t);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public TarifaDTO crearTarifa(Long ayuntamientoId, TarifaDTO dto) {
        Ayuntamiento a = validatePermissions(ayuntamientoId);

        Tarifa t = new Tarifa();
        t.setAyuntamiento(a);
        t.setPrecioBase(new BigDecimal(dto.getPrecioBase()));

        tarifaRepo.save(t);
        return mapToDTO(t);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public TarifaDTO actualizarTarifa(Long ayuntamientoId, TarifaDTO dto) {
        Tarifa t = tarifaRepo.findByAyuntamientoId(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));

        t.setPrecioBase(new BigDecimal(dto.getPrecioBase()));
        tarifaRepo.save(t);

        return mapToDTO(t);
    }

    private TarifaDTO mapToDTO(Tarifa t) {
        TarifaDTO dto = new TarifaDTO();
        dto.setId(t.getId());
        dto.setAyuntamientoId(t.getAyuntamiento().getId());
        dto.setPrecioBase(t.getPrecioBase().toString());
        return dto;
    }

    // ================= FRANJAS ================

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    @Transactional
    public TarifaFranjaDTO crearFranja(Long ayuntamientoId, TarifaFranjaDTO dto) {

        Ayuntamiento ayto = validatePermissions(ayuntamientoId);

        if (dto.getHoraFin() <= dto.getHoraInicio()) {
            throw new IllegalArgumentException("horaFin debe ser mayor que horaInicio");
        }

        var existentes = franjaRepo.findByAyuntamientoId(ayuntamientoId);
        boolean solapa = existentes.stream().anyMatch(fr ->
                dto.getHoraInicio() < fr.getHoraFin() &&
                        dto.getHoraFin() > fr.getHoraInicio()
        );

        if (solapa) throw new IllegalArgumentException("La franja se solapa con otra existente");

        TarifaFranja fr = new TarifaFranja();
        fr.setAyuntamiento(ayto);
        updateEntityFromDTO(fr, dto);

        return toDTO(franjaRepo.save(fr));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public List<TarifaFranjaDTO> listarFranjas(Long ayuntamientoId) {

        validatePermissions(ayuntamientoId);

        return franjaRepo.findByAyuntamientoId(ayuntamientoId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    @Transactional
    public TarifaFranjaDTO actualizarFranja(Long ayuntamientoId, Long franjaId, TarifaFranjaDTO dto) {

        Ayuntamiento a = validatePermissions(ayuntamientoId);

        TarifaFranja franja = franjaRepo.findById(franjaId)
                .orElseThrow(() -> new RuntimeException("Franja no encontrada"));

        if (!franja.getAyuntamiento().getId().equals(ayuntamientoId)) {
            throw new SecurityException("La franja no pertenece a este ayuntamiento");
        }

        if (dto.getHoraFin() <= dto.getHoraInicio()) {
            throw new IllegalArgumentException("horaFin debe ser mayor que horaInicio");
        }

        var existentes = franjaRepo.findByAyuntamientoId(ayuntamientoId)
                .stream()
                .filter(f -> !f.getId().equals(franjaId))
                .toList();

        boolean solapa = existentes.stream().anyMatch(fr ->
                dto.getHoraInicio() < fr.getHoraFin() &&
                        dto.getHoraFin() > fr.getHoraInicio()
        );

        if (solapa) throw new IllegalArgumentException("La franja se solapa con otra existente");

        updateEntityFromDTO(franja, dto);

        return toDTO(franjaRepo.save(franja));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    @Transactional
    public void eliminarFranja(Long ayuntamientoId, Long franjaId) {

        validatePermissions(ayuntamientoId);

        TarifaFranja franja = franjaRepo.findById(franjaId)
                .orElseThrow(() -> new RuntimeException("Franja no encontrada"));

        franjaRepo.delete(franja);
    }

    private TarifaFranjaDTO toDTO(TarifaFranja fr) {
        TarifaFranjaDTO dto = new TarifaFranjaDTO();
        dto.setId(fr.getId());
        dto.setHoraInicio(fr.getHoraInicio());
        dto.setHoraFin(fr.getHoraFin());
        dto.setPrecio(fr.getPrecio());
        return dto;
    }

    private void updateEntityFromDTO(TarifaFranja fr, TarifaFranjaDTO dto) {
        fr.setHoraInicio(dto.getHoraInicio());
        fr.setHoraFin(dto.getHoraFin());
        fr.setPrecio(dto.getPrecio());
    }
}
