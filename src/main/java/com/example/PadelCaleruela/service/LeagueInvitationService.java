package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueInvitationDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueInvitationRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeagueInvitationService {

    private final LeagueInvitationRepository invitationRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;

    public LeagueInvitationService(
            LeagueInvitationRepository invitationRepository,
            LeagueRepository leagueRepository,
            UserRepository userRepository) {
        this.invitationRepository = invitationRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
    }

    public LeagueInvitationDTO sendInvitation(Long leagueId, Long senderId, Long receiverId, LeagueInvitationType type) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("Liga no encontrada"));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Remitente no encontrado"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Destinatario no encontrado"));

        // Evitar duplicados
        if (invitationRepository.existsByLeague_IdAndReceiver_Id(leagueId, receiverId)) {
            throw new RuntimeException("Ya existe una invitación para este jugador");
        }

        LeagueInvitation invitation = new LeagueInvitation();
        invitation.setLeague(league);
        invitation.setSender(sender);
        invitation.setReceiver(receiver);
        invitation.setType(type);
        invitationRepository.save(invitation);

        return mapToDto(invitation);
    }

    public List<LeagueInvitationDTO> getInvitationsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return invitationRepository.findByReceiverAndStatus(user, InvitationStatus.PENDING)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }


    public void respondToInvitation(Long invitationId, boolean accepted) {
        LeagueInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada"));

        if (accepted) {
            invitation.setStatus(InvitationStatus.ACCEPTED);
            // Agregar al jugador a la liga
            League league = invitation.getLeague();
            league.getPlayers().add(invitation.getReceiver());
            leagueRepository.save(league);
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
