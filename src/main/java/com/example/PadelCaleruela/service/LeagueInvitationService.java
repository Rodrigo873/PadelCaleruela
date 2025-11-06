package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueInvitationDTO;
import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueInvitation;
import com.example.PadelCaleruela.model.User;
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

    public LeagueInvitationDTO sendInvitation(Long leagueId, Long senderId, Long receiverId) {
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

        // Validar permisos: solo creador o si se permiten invitaciones
        boolean canInvite = league.getCreator().getId().equals(senderId)
                || league.isAllowPlayerInvites();

        if (!canInvite) {
            throw new RuntimeException("No tienes permiso para invitar jugadores a esta liga");
        }

        LeagueInvitation invitation = new LeagueInvitation();
        invitation.setLeague(league);
        invitation.setSender(sender);
        invitation.setReceiver(receiver);
        invitationRepository.save(invitation);

        return mapToDto(invitation);
    }

    public List<LeagueInvitationDTO> getInvitationsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return invitationRepository.findByReceiver(user)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public void respondToInvitation(Long invitationId, boolean accepted) {
        LeagueInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada"));

        if (accepted) {
            invitation.setStatus(LeagueInvitation.InvitationStatus.ACCEPTED);
            // Agregar al jugador a la liga
            League league = invitation.getLeague();
            league.getPlayers().add(invitation.getReceiver());
            leagueRepository.save(league);
        } else {
            invitation.setStatus(LeagueInvitation.InvitationStatus.REJECTED);
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
        dto.setStatus(inv.getStatus().name());
        dto.setSentAt(inv.getSentAt());
        return dto;
    }
}
