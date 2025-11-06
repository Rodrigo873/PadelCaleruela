package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.InvitationDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.InvitationRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public InvitationService(InvitationRepository invitationRepository,
                             ReservationRepository reservationRepository,
                             UserRepository userRepository) {
        this.invitationRepository = invitationRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public String respondToInvitation(Long invitationId, String response, String principalName) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada"));


        User receiver = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + principalName));

        if (!invitation.getReceiver().getId().equals(receiver.getId()))
            throw new RuntimeException("No tienes permiso para responder esta invitación.");

        if (response.equalsIgnoreCase("accept")) {
            invitation.setStatus(InvitationStatus.ACCEPTED);

            Reservation reservation = reservationRepository.findById(invitation.getReservation().getId())
                    .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

            boolean yaPresente = reservation.getJugadores().stream()
                    .anyMatch(u -> u.getId().equals(receiver.getId()));

            if (!yaPresente) {
                reservation.getJugadores().add(receiver);
            }


            reservationRepository.save(reservation);
            invitationRepository.save(invitation);

            return "✅ Invitación aceptada correctamente.";
        }

        if (response.equalsIgnoreCase("reject")) {
            invitation.setStatus(InvitationStatus.REJECTED);
            invitationRepository.save(invitation);
            return "❌ Invitación rechazada correctamente.";
        }

        throw new RuntimeException("Respuesta inválida. Usa 'accept' o 'reject'.");
    }


    @Transactional
    public String joinPublicReservationWithInvitation(Long reservationId, String principalName) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        if (!reservation.isPublic()) {
            throw new RuntimeException("Esta reserva no es pública.");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING &&
                reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new RuntimeException("Solo puedes unirte a reservas pendientes o confirmadas.");
        }

        if (reservation.getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("No puedes unirte a una reserva ya iniciada o finalizada.");
        }

        if (reservation.getJugadores().size() >= 4) {
            throw new RuntimeException("La reserva ya está completa (máximo 4 jugadores).");
        }

        // 🔹 Obtener el usuario que se une
        User joiningUser = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + principalName));

        // 🔹 Validar que no sea el creador
        if (reservation.getUser().getId().equals(joiningUser.getId())) {
            throw new RuntimeException("Ya eres el creador de esta reserva.");
        }

        // 🔹 Evitar duplicados
        boolean yaPresente = reservation.getJugadores().stream()
                .anyMatch(u -> u.getId().equals(joiningUser.getId()));

        if (yaPresente) {
            throw new RuntimeException("Ya estás unido a esta reserva.");
        }

        // ✅ Añadir jugador a la reserva
        reservation.getJugadores().add(joiningUser);
        reservationRepository.save(reservation);

        // 📨 Crear una “invitación aceptada” automáticamente
        Invitation invitation = new Invitation();
        invitation.setReservation(reservation);
        invitation.setSender(reservation.getUser()); // Creador de la reserva
        invitation.setReceiver(joiningUser); // Usuario que se une
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setCreatedAt(LocalDateTime.now());

        invitationRepository.save(invitation);

        return "✅ Te has unido correctamente a la reserva pública.";
    }

    public List<InvitationDTO> getPendingInvitations(Long userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        var invitations = invitationRepository.findByReceiverAndStatus(user, InvitationStatus.PENDING);

        return invitations.stream().map(inv -> {
            var dto = new InvitationDTO();
            dto.setId(inv.getId());
            dto.setSenderUsername(inv.getSender().getUsername());
            dto.setReceiverUsername(inv.getReceiver().getUsername());
            dto.setCreatedAt(inv.getCreatedAt());
            dto.setStatus(inv.getStatus());

            if (inv.getReservation() != null) {
                dto.setReservationId(inv.getReservation().getId());
                dto.setStartTime(inv.getReservation().getStartTime());
                dto.setEndTime(inv.getReservation().getEndTime());
            }

            return dto;
        }).toList();
    }



}
