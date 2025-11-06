package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.LeagueInvitationDTO;
import com.example.PadelCaleruela.service.LeagueInvitationService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/invitations")
@CrossOrigin(origins = "*")
public class LeagueInvitationController {

    private final LeagueInvitationService invitationService;

    public LeagueInvitationController(LeagueInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/send/{leagueId}/{senderId}/{receiverId}")
    public LeagueInvitationDTO sendInvitation(@PathVariable Long leagueId,
                                              @PathVariable Long senderId,
                                              @PathVariable Long receiverId) {
        return invitationService.sendInvitation(leagueId, senderId, receiverId);
    }

    @GetMapping("/user/{userId}")
    public List<LeagueInvitationDTO> getUserInvitations(@PathVariable Long userId) {
        return invitationService.getInvitationsForUser(userId);
    }

    @PostMapping("/{invitationId}/respond")
    public void respondToInvitation(@PathVariable Long invitationId,
                                    @RequestParam boolean accepted) {
        invitationService.respondToInvitation(invitationId, accepted);
    }
}
