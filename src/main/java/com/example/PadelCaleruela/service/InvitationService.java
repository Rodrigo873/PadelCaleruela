package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.InvitationDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.InvitationRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
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

    private final AuthService authService;

    public InvitationService(InvitationRepository invitationRepository,
                             ReservationRepository reservationRepository,
                             UserRepository userRepository,
                             AuthService authService) {
        this.invitationRepository = invitationRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }


    @Transactional
    public String respondToInvitation(Long invitationId, String response, String principalName) {

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada."));

        User receiver = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + principalName));

        Reservation reservation = invitation.getReservation();

        // 🔐 Validación multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
            authService.ensureSameAyuntamiento(receiver.getAyuntamiento());
        }

        // USER → solo puede responder sus invitaciones
        if (authService.isUser() && !invitation.getReceiver().getId().equals(receiver.getId())) {
            throw new RuntimeException("No puedes responder invitaciones de otros usuarios.");
        }

        // ADMIN → puede gestionar solo invitados de su ayuntamiento
        if (authService.isAdmin() &&
                !invitation.getReceiver().getAyuntamiento().getId()
                        .equals(authService.getCurrentUser().getAyuntamiento().getId())) {

            throw new RuntimeException("No puedes gestionar invitaciones de otro ayuntamiento.");
        }

        if (!invitation.getReceiver().getId().equals(receiver.getId()) &&
                !authService.isAdmin() &&
                !authService.isSuperAdmin()) {
            throw new RuntimeException("No tienes permiso para responder esta invitación.");
        }

        // 🔎 Obtener todas las invitaciones previas
        List<Invitation> allForUser = invitationRepository
                .findAllByReservationIdAndReceiverIdOrderByCreatedAtAsc(
                        reservation.getId(), receiver.getId());

        boolean yaPresente = reservation.getJugadores().stream()
                .anyMatch(u -> u.getId().equals(receiver.getId()));

        // ACEPTAR ✔️
        if (response.equalsIgnoreCase("accept")) {

            if (!yaPresente && reservation.getJugadores().size() >= 4)
                throw new RuntimeException("La reserva ya está completa.");

            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setCreatedAt(LocalDateTime.now());

            // Cancelar el resto
            for (Invitation inv : allForUser) {
                if (!inv.getId().equals(invitation.getId())) {
                    inv.setStatus(InvitationStatus.REJECTED);
                }
            }

            if (!yaPresente) reservation.getJugadores().add(receiver);

            reservationRepository.save(reservation);
            invitationRepository.saveAll(allForUser);

            return "✅ Invitación aceptada.";
        }

        // RECHAZAR ❌
        if (response.equalsIgnoreCase("reject")) {

            invitation.setStatus(InvitationStatus.REJECTED);
            invitation.setCreatedAt(LocalDateTime.now());

            if (yaPresente)
                reservation.getJugadores().removeIf(u -> u.getId().equals(receiver.getId()));

            reservationRepository.save(reservation);
            invitationRepository.save(invitation);

            return "❌ Invitación rechazada.";
        }

        throw new RuntimeException("Respuesta inválida. Usa 'accept' o 'reject'.");
    }




    @Transactional(readOnly = true)
    public Long getMyInvitationIdForReservation(Long reservationId, String principalName) {

        User me = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
            authService.ensureSameAyuntamiento(me.getAyuntamiento());
        }

        Optional<Invitation> opt = invitationRepository
                .findTopByReservation_IdAndReceiver_IdAndStatusOrderByIdDesc(
                        reservationId, me.getId(), InvitationStatus.PENDING);

        return opt.map(Invitation::getId).orElse(null);
    }


    @Transactional
    public String joinPublicReservationWithInvitation(Long reservationId, String principalName) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        User joiningUser = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 🔐 Multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
            authService.ensureSameAyuntamiento(joiningUser.getAyuntamiento());
        }

        // Validaciones estándar...
        if (!reservation.isPublic()) throw new RuntimeException("No es pública.");
        if (reservation.getStartTime().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Ya comenzó.");
        if (reservation.getJugadores().size() >= 4) throw new RuntimeException("Llena.");
        if (reservation.getUser().getId().equals(joiningUser.getId()))
            throw new RuntimeException("Eres el creador.");

        boolean yaPresente = reservation.getJugadores().stream()
                .anyMatch(p -> p.getId().equals(joiningUser.getId()));

        if (yaPresente) throw new RuntimeException("Ya estás unido.");

        // 🔎 Buscar invitaciones previas
        List<Invitation> all = invitationRepository
                .findAllByReservationIdAndReceiverIdOrderByCreatedAtAsc(
                        reservationId, joiningUser.getId());

        Invitation invitation;

        if (!all.isEmpty()) {
            invitation = all.get(all.size() - 1);
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setCreatedAt(LocalDateTime.now());

            for (Invitation inv : all) {
                if (!inv.getId().equals(invitation.getId())) {
                    inv.setStatus(InvitationStatus.REJECTED);
                }
            }

            invitationRepository.saveAll(all);

        } else {
            invitation = new Invitation();
            invitation.setReservation(reservation);
            invitation.setSender(reservation.getUser());
            invitation.setReceiver(joiningUser);
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setCreatedAt(LocalDateTime.now());
            invitationRepository.save(invitation);
        }

        reservation.getJugadores().add(joiningUser);
        reservationRepository.save(reservation);

        return "✅ Te has unido correctamente.";
    }




    public List<InvitationDTO> getPendingInvitations(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin()) {
            // ADMIN → solo mismo ayuntamiento
            authService.ensureSameAyuntamiento(user.getAyuntamiento());

            // USER → solo puede ver las suyas
            if (authService.isUser() && !current.getId().equals(userId)) {
                throw new RuntimeException("No puedes ver invitaciones de otros usuarios.");
            }
        }

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


    public long getPendingCount(Long userId) {
        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver invitaciones de otro usuario.");
        }

        return invitationRepository.countPendingByUserId(userId);
    }

    public boolean hasPending(Long userId) {
        return getPendingCount(userId) > 0;
    }




}
