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
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada."));

        User receiver = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + principalName));

        if (!invitation.getReceiver().getId().equals(receiver.getId())) {
            throw new RuntimeException("No tienes permiso para responder esta invitación.");
        }

        Reservation reservation = invitation.getReservation();

        // ✅ Validaciones coherentes con joinPublicReservationWithInvitation
        if (!reservation.isPublic()) {
            throw new RuntimeException("Esta reserva no es pública.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING &&
                reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new RuntimeException("Solo puedes responder invitaciones de reservas pendientes o confirmadas.");
        }
        if (reservation.getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("No puedes responder a una reserva ya iniciada o finalizada.");
        }

        // 🔎 Traemos TODAS las invitaciones para normalizar estados
        List<Invitation> allForUser = invitationRepository
                .findAllByReservationIdAndReceiverIdOrderByCreatedAtAsc(reservation.getId(), receiver.getId());

        boolean yaPresente = reservation.getJugadores().stream()
                .anyMatch(u -> u.getId().equals(receiver.getId()));

        if (response.equalsIgnoreCase("accept")) {
            // Capacidad: solo bloquea si NO estás dentro y ya hay 4
            if (!yaPresente && reservation.getJugadores().size() >= 4) {
                throw new RuntimeException("La reserva ya está completa (máximo 4 jugadores).");
            }

            // 1) Aceptamos la actual
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setCreatedAt(LocalDateTime.now());

            // 2) Cancelamos el resto (para evitar “dos aceptadas” o “pendientes” huérfanas)
            for (Invitation inv : allForUser) {
                if (!inv.getId().equals(invitation.getId())) {
                    if (inv.getStatus() != InvitationStatus.REJECTED) {
                        inv.setStatus(InvitationStatus.REJECTED);
                        // Opcional: guarda timestamp de actualización si lo tienes
                    }
                }
            }

            // 3) Añadimos al jugador si no estaba
            if (!yaPresente) {
                reservation.getJugadores().add(receiver);
            }

            // Persistimos
            reservationRepository.save(reservation);
            invitationRepository.saveAll(allForUser);
            invitationRepository.save(invitation); // (ya incluido arriba si quieres, pero explícito)

            return "✅ Invitación aceptada. Te has unido a la reserva.";

        } else if (response.equalsIgnoreCase("reject")) {
            // Marca la actual como rechazada
            invitation.setStatus(InvitationStatus.REJECTED);
            invitation.setCreatedAt(LocalDateTime.now());

            // Si estaba dentro, lo quitamos (abandono)
            if (yaPresente) {
                reservation.getJugadores().removeIf(u -> u.getId().equals(receiver.getId()));
            }

            reservationRepository.save(reservation);
            invitationRepository.save(invitation);

            return "❌ Invitación rechazada correctamente.";
        }

        throw new RuntimeException("Respuesta inválida. Usa 'accept' o 'reject'.");
    }




    @Transactional(readOnly = true)
    public Long getMyInvitationIdForReservation(Long reservationId, String principalName) {
        User me = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + principalName));

        Optional<Invitation> opt = invitationRepository
                .findTopByReservation_IdAndReceiver_IdAndStatusOrderByIdDesc(
                        reservationId,
                        me.getId(),
                        InvitationStatus.PENDING
                );

        return opt.map(Invitation::getId).orElse(null);
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

        // 🔹 Evitar duplicados en la lista de jugadores
        boolean yaPresente = reservation.getJugadores().stream()
                .anyMatch(u -> u.getId().equals(joiningUser.getId()));

        if (yaPresente) {
            throw new RuntimeException("Ya estás unido a esta reserva.");
        }

        // 🔹 Buscar todas las invitaciones previas de este usuario a esta reserva
        List<Invitation> allInvitations = invitationRepository
                .findAllByReservationIdAndReceiverIdOrderByCreatedAtAsc(reservationId, joiningUser.getId());

        Invitation invitation = null;

        if (!allInvitations.isEmpty()) {
            // Tomamos la última invitación (la más reciente)
            invitation = allInvitations.get(allInvitations.size() - 1);

            if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
                throw new RuntimeException("Ya estás unido mediante una invitación aceptada.");
            }

            // Si estaba rechazada, pendiente o cancelada, la reactivamos
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setCreatedAt(LocalDateTime.now());
            invitationRepository.save(invitation);

            // Cancelamos cualquier otra invitación anterior
            for (Invitation inv : allInvitations) {
                if (!inv.getId().equals(invitation.getId())) {
                    if (inv.getStatus() != InvitationStatus.REJECTED) {
                        inv.setStatus(InvitationStatus.REJECTED);
                    }
                }
            }
            invitationRepository.saveAll(allInvitations);

        } else {
            // 📨 Crear nueva invitación aceptada
            invitation = new Invitation();
            invitation.setReservation(reservation);
            invitation.setSender(reservation.getUser()); // creador
            invitation.setReceiver(joiningUser);
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setCreatedAt(LocalDateTime.now());
            invitationRepository.save(invitation);
        }

        // ✅ Añadir jugador a la reserva
        reservation.getJugadores().add(joiningUser);
        reservationRepository.save(reservation);

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
