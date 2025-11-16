package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueInvitationDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueInvitationRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.UserRepository;
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


    public LeagueInvitationService(
            LeagueInvitationRepository invitationRepository,
            LeagueRepository leagueRepository,
            UserRepository userRepository,
            AuthService authService
    ) {
        this.invitationRepository = invitationRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.authService = authService;
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

        LeagueInvitation invitation = new LeagueInvitation();
        invitation.setLeague(league);
        invitation.setSender(sender);
        invitation.setReceiver(receiver);
        invitation.setType(type);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setSentAt(LocalDateTime.now());

        invitationRepository.save(invitation);

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



    public void respondToInvitation(Long invitationId, boolean accepted) {

        User current = authService.getCurrentUser();

        LeagueInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada"));

        User receiver = invitation.getReceiver();
        League league = invitation.getLeague();

        // USER → solo puede responder sus invitaciones
        if (authService.isUser() && !current.getId().equals(receiver.getId())) {
            throw new RuntimeException("No puedes responder invitaciones de otro usuario.");
        }

        // ADMIN → solo si pertenece al mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            authService.ensureSameAyuntamiento(receiver.getAyuntamiento());
        }

        // SUPERADMIN → no tiene restricciones

        if (accepted) {
            invitation.setStatus(InvitationStatus.ACCEPTED);

            // Evitar duplicados en la liga
            boolean alreadyInLeague = league.getPlayers().stream()
                    .anyMatch(u -> u.getId().equals(receiver.getId()));

            if (!alreadyInLeague) {
                league.getPlayers().add(receiver);
                leagueRepository.save(league);
            }

        } else {
            invitation.setStatus(InvitationStatus.REJECTED);
        }

        invitationRepository.save(invitation);
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
        return dto;
    }
}
