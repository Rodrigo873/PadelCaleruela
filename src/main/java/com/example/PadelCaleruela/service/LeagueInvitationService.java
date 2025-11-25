package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueInvitationDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueInvitationRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeagueInvitationService {

    private final LeagueInvitationRepository invitationRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    private final UserNotificationService userNotificationService;
    private final NotificationAppService notificationAppService;
    private final NotificationFactory notificationFactory;


    public LeagueInvitationService(
            LeagueInvitationRepository invitationRepository,
            LeagueRepository leagueRepository,
            UserRepository userRepository,
            AuthService authService,
            UserNotificationService userNotificationService,
            NotificationAppService notificationAppService,
            NotificationFactory notificationFactory
    ) {
        this.invitationRepository = invitationRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.userNotificationService=userNotificationService;
        this.notificationAppService=notificationAppService;
        this.notificationFactory=notificationFactory;
    }

    public LeagueInvitationDTO sendInvitation(Long leagueId, Long senderId, Long receiverId, LeagueInvitationType type) {

        User current = authService.getCurrentUser();

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("Liga no encontrada"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Remitente no encontrado"));

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Destinatario no encontrado"));

        // 🔐 Validación multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            authService.ensureSameAyuntamiento(sender.getAyuntamiento());
            authService.ensureSameAyuntamiento(receiver.getAyuntamiento());
        }

        // ADMIN → solo dentro de su ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
        }

        // Evitar duplicados activos
        if (invitationRepository.existsByLeague_IdAndReceiver_Id(leagueId, receiverId)) {
            throw new RuntimeException("Ya existe una invitación para este jugador.");
        }

        // 🔹 Crear invitación
        LeagueInvitation invitation = new LeagueInvitation();
        invitation.setLeague(league);
        invitation.setSender(sender);
        invitation.setReceiver(receiver);
        invitation.setType(type);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setSentAt(LocalDateTime.now());

        invitationRepository.save(invitation);

        // 🔔 Enviar notificación push al jugador invitado
        try {
            userNotificationService.sendToUser(
                    receiver.getId(),
                    sender.getUsername(),
                    NotificationType.LEAGUE_INVITATION
            );
        } catch (Exception e) {
            System.err.println("❌ Error enviando notificación de liga a " +
                    receiver.getUsername() + ": " + e.getMessage());
        }

        return mapToDto(invitation);
    }


    public long getPendingCount(Long userId) {
        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver invitaciones de otro usuario.");
        }

        return invitationRepository.countByReceiverIdAndStatus(userId, InvitationStatus.PENDING);
    }


    public List<LeagueInvitationDTO> getInvitationsForUser(Long userId) {

        User current = authService.getCurrentUser();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // USER → solo puede ver las suyas
        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new RuntimeException("No puedes ver invitaciones de otros usuarios.");
        }

        // ADMIN → solo en su ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(user.getAyuntamiento());
        }

        List<LeagueInvitation> pending = invitationRepository.findByReceiverIdAndStatus(userId, InvitationStatus.PENDING);

        return pending.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }


    @Transactional
    public void respondToInvitation(Long invitationId, boolean accepted) {

        User current = authService.getCurrentUser();

        LeagueInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada"));

        User receiver = invitation.getReceiver();
        User sender = invitation.getSender();
        League league = invitation.getLeague();

        // USER → solo responder sus propias invitaciones
        if (authService.isUser() && !current.getId().equals(receiver.getId())) {
            throw new RuntimeException("No puedes responder invitaciones de otro usuario.");
        }

        // ADMIN → mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            authService.ensureSameAyuntamiento(receiver.getAyuntamiento());
        }

        // SUPERADMIN → sin restricciones

        if (accepted) {

            invitation.setStatus(InvitationStatus.ACCEPTED);

            boolean alreadyInLeague = league.getPlayers()
                    .stream()
                    .anyMatch(u -> u.getId().equals(receiver.getId()));

            if (!alreadyInLeague) {
                league.getPlayers().add(receiver);
                leagueRepository.save(league);
            }

            // ------------------------------------------
            // 🔔 NOTIFICACIONES (PUSH + BD)
            // ------------------------------------------
            try {
                // 1️⃣ Avisar al creador de la liga (se ha unido alguien nuevo)
                sendAndSaveNotification(
                        sender,              // destinatario
                        receiver,            // quien acepta
                        NotificationType.LEAGUE_INVITATION_ACCEPT,
                        null                 // no hay reserva → pasamos null
                );

                // 2️⃣ Avisar a todos los jugadores de la liga excepto el que entra
                for (User jugador : league.getPlayers()) {

                    if (!jugador.getId().equals(receiver.getId())) {

                        sendAndSaveNotification(
                                jugador,              // destinatario
                                receiver,             // quien se une
                                NotificationType.LEAGUE_JOINED_WITH_YOU,
                                null
                        );
                    }
                }

            } catch (Exception e) {
                System.out.println("⚠ Error enviando notificaciones push/bd (aceptar liga): " + e.getMessage());
            }

        } else {

            invitation.setStatus(InvitationStatus.REJECTED);

            // ------------------------------------------
            // 🔔 NOTIFICACIÓN RECHAZADA
            // ------------------------------------------
            try {
                sendAndSaveNotification(
                        sender,                 // creador
                        receiver,               // quien rechaza
                        NotificationType.LEAGUE_INVITATION_REJECT,
                        null
                );

            } catch (Exception e) {
                System.out.println("⚠ Error enviando notificación push/bd (rechazar liga): " + e.getMessage());
            }
        }

        invitationRepository.save(invitation);
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



    private LeagueInvitationDTO mapToDto(LeagueInvitation inv) {
        LeagueInvitationDTO dto = new LeagueInvitationDTO();
        dto.setId(inv.getId());
        dto.setLeagueId(inv.getLeague().getId());
        dto.setLeagueName(inv.getLeague().getName());
        dto.setSenderId(inv.getSender().getId());
        dto.setSenderName(inv.getSender().getUsername());
        dto.setReceiverId(inv.getReceiver().getId());
        dto.setReceiverName(inv.getReceiver().getUsername());
        dto.setType(inv.getType());
        dto.setStatus(inv.getStatus().name());
        dto.setSentAt(inv.getSentAt());
        dto.setSenderProfileImageUrl(
                inv.getSender().getProfileImageUrl()   // o cualquier otra propiedad
        );
        return dto;
    }
}
