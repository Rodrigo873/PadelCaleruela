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
    private final UserNotificationService userNotificationService;
    private final NotificationFactory notificationFactory;
    private final NotificationAppService notificationAppService;


    public InvitationService(InvitationRepository invitationRepository,
                             ReservationRepository reservationRepository,
                             UserRepository userRepository,
                             AuthService authService,UserNotificationService userNotificationService,
                             NotificationAppService notificationAppService,
                             NotificationFactory notificationFactory) {
        this.invitationRepository = invitationRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.userNotificationService=userNotificationService;
        this.notificationAppService=notificationAppService;
        this.notificationFactory=notificationFactory;
    }


    @Transactional
    public String respondToInvitation(Long invitationId, String response, String principalName) {

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada."));

        User receiver = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + principalName));

        Reservation reservation = invitation.getReservation();
        User creator = reservation.getUser();

        // 🔐 Multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
            authService.ensureSameAyuntamiento(receiver.getAyuntamiento());
        }

        // USER → solo responder sus propias invitaciones
        if (authService.isUser() && !invitation.getReceiver().getId().equals(receiver.getId())) {
            throw new RuntimeException("No puedes responder invitaciones de otros usuarios.");
        }

        // ADMIN → solo en su ayuntamiento
        if (authService.isAdmin() &&
                !invitation.getReceiver().getAyuntamiento().getId()
                        .equals(authService.getCurrentUser().getAyuntamiento().getId())) {

            throw new RuntimeException("No puedes gestionar invitaciones de otro ayuntamiento.");
        }

        boolean yaPresente = reservation.getJugadores().stream()
                .anyMatch(u -> u.getId().equals(receiver.getId()));

        // Todas las invitaciones a esta reserva para este usuario
        List<Invitation> allForUser = invitationRepository
                .findAllByReservationIdAndReceiverIdOrderByCreatedAtAsc(
                        reservation.getId(), receiver.getId());


        // --------------------------------------------------------------------------
        // ✔ ACEPTAR INVITACIÓN
        // --------------------------------------------------------------------------
        if (response.equalsIgnoreCase("accept")) {

            if (!yaPresente && reservation.getJugadores().size() >= 4)
                throw new RuntimeException("La reserva ya está completa.");

            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setCreatedAt(LocalDateTime.now());

            // Rechazar duplicadas
            for (Invitation inv : allForUser) {
                if (!inv.getId().equals(invitation.getId())) {
                    inv.setStatus(InvitationStatus.REJECTED);
                }
            }

            if (!yaPresente) reservation.getJugadores().add(receiver);

            reservationRepository.save(reservation);
            invitationRepository.saveAll(allForUser);

            // ----------------------------------------------------------------------
            // 🔔 NOTIFICACIONES PUSH + BASE DE DATOS
            // ----------------------------------------------------------------------
            try {

                // 1️⃣ Al creador
                sendAndSaveNotification(
                        creator,
                        receiver,
                        NotificationType.MATCH_INVITATION_ACCEPTED,
                        reservation
                );

                // 2️⃣ A los jugadores ya dentro (menos el que entra)
                for (User jugador : reservation.getJugadores()) {
                    if (!jugador.getId().equals(receiver.getId())) {
                        sendAndSaveNotification(
                                jugador,
                                receiver,
                                NotificationType.MATCH_JOINED_WITH_YOU,
                                reservation
                        );
                    }
                }

            } catch (Exception e) {
                System.out.println("⚠ Error enviando notificaciones (aceptar invitación): " + e.getMessage());
            }

            return "✅ Invitación aceptada.";
        }


        // --------------------------------------------------------------------------
        // ❌ RECHAZAR INVITACIÓN
        // --------------------------------------------------------------------------
        if (response.equalsIgnoreCase("reject")) {

            invitation.setStatus(InvitationStatus.REJECTED);
            invitation.setCreatedAt(LocalDateTime.now());

            if (yaPresente)
                reservation.getJugadores().removeIf(u -> u.getId().equals(receiver.getId()));

            reservationRepository.save(reservation);
            invitationRepository.save(invitation);

            // ----------------------------------------------------------------------
            // 🔔 NOTIFICACIÓN DE RECHAZO (PUSH + BD)
            // ----------------------------------------------------------------------
            try {
                sendAndSaveNotification(
                        creator,
                        receiver,
                        NotificationType.MATCH_INVITATION_REJECTED,
                        reservation
                );
            } catch (Exception e) {
                System.out.println("⚠ Error enviando notificación (rechazo invitación): " + e.getMessage());
            }

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


        // -----------------------------------------------------------------
        // 🔔 NOTIFICACIONES (PUSH + BD) — COMPLETAMENTE IMPLEMENTADAS
        // -----------------------------------------------------------------
        try {

            User creator = reservation.getUser();

            // 1️⃣ Al creador de la reserva (alguien se une)
            sendAndSaveNotification(
                    creator,
                    joiningUser,
                    NotificationType.PUBLIC_MATCH_JOINED,
                    reservation
            );

            // 2️⃣ A los jugadores que ya estaban dentro (salvo el que entra)
            for (User jugador : reservation.getJugadores()) {
                if (!jugador.getId().equals(joiningUser.getId())) {

                    sendAndSaveNotification(
                            jugador,
                            joiningUser,
                            NotificationType.MATCH_JOINED_WITH_YOU,
                            reservation
                    );
                }
            }

        } catch (Exception e) {
            System.out.println("⚠ Error enviando notificaciones (joinPublicReservation): " + e.getMessage());
        }


        return "✅ Te has unido correctamente.";
    }


    private void sendAndSaveNotification(
            User targetUser,
            User fromUser,
            NotificationType type,
            Reservation reservation
    ) {
        String title = notificationFactory.getTitle(type);
        String body = notificationFactory.getMessage(type,
                fromUser != null ? fromUser.getUsername() : "Sistema");

        String extraJson = null;

        if (reservation != null) {
            extraJson = """
        {
            "reservationId": %d
        }
        """.formatted(reservation.getId());
        }

        try {
            userNotificationService.sendToUser(targetUser.getId(), fromUser.getUsername(), type);
        } catch (Exception e) {
            System.out.println("⚠ Error enviando push: " + e.getMessage());
        }

        Notification n = new Notification();
        n.setUserId(targetUser.getId());
        n.setSenderId(fromUser != null ? fromUser.getId() : null);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(body);
        n.setExtraData(extraJson);

        notificationAppService.saveNotification(n);
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

        // 🔹 Filtrado por usuario receptor Y su ayuntamiento
        var invitations = invitationRepository.findByReceiverAndStatus(user, InvitationStatus.PENDING)
                .stream()
                .filter(inv -> inv.getSender().getAyuntamiento().equals(user.getAyuntamiento()))
                .toList();

        return invitations.stream().map(inv -> {
            var dto = new InvitationDTO();
            dto.setId(inv.getId());
            dto.setSenderUsername(inv.getSender().getUsername());
            dto.setReceiverUsername(inv.getReceiver().getUsername());
            dto.setCreatedAt(inv.getCreatedAt());
            dto.setStatus(inv.getStatus());
            dto.setSenderProfileImageUrl(inv.getSender().getProfileImageUrl());

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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 🔹 Contamos solo las del mismo ayuntamiento
        return invitationRepository.findByReceiverAndStatus(user, InvitationStatus.PENDING)
                .stream()
                .filter(inv -> inv.getSender().getAyuntamiento().equals(user.getAyuntamiento()))
                .count();
    }


    public boolean hasPending(Long userId) {
        return getPendingCount(userId) > 0;
    }




}
