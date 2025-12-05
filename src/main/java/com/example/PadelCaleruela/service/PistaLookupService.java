package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PistaLookupService {

    private final ReservationRepository reservationRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public Long getPistaIdFromReservation(Long reservaId, Long userId) {

        System.out.println("🔎 Buscando pista para reserva " + reservaId + " por user " + userId);

        Reservation reserva = reservationRepo.findById(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String rol = user.getRole().name();

        // SUPERADMIN y ADMIN → pueden siempre
        if (!rol.equals("SUPERADMIN") && !rol.equals("ADMIN")) {

            boolean esCreador = reserva.getUser().getId().equals(userId);
            boolean esJugador = reserva.getJugadores().stream()
                    .anyMatch(u -> u.getId().equals(userId));

            if (!esCreador && !esJugador) {
                throw new RuntimeException("No tienes permiso para ver esta pista.");
            }
        }

        if (reserva.getPista() == null) {
            throw new RuntimeException("La reserva no tiene pista asignada");
        }

        Long pistaId = reserva.getPista().getId();

        System.out.println("✔ Pista encontrada: " + pistaId);

        return pistaId;
    }
}
