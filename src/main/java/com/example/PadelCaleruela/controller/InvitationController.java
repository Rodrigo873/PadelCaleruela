package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.InvitationDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.InvitationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import com.example.PadelCaleruela.service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final InvitationService invitationService;

    @GetMapping("/pending/{userId}")
    public List<InvitationDTO> getPendingInvitations(@PathVariable Long userId) {
        return invitationService.getPendingInvitations(userId);
    }

    @GetMapping("/pending/count/{userId}")
    public long getPendingCount(@PathVariable Long userId) {
        return invitationService.getPendingCount(userId);
    }

    @GetMapping("/pending/has/{userId}")
    public boolean hasPending(@PathVariable Long userId) {
        return invitationService.hasPending(userId);
    }


    @PutMapping("/{invitationId}/respond")
    public ResponseEntity<String> respondInvitation(
            @PathVariable Long invitationId,
            @RequestParam String response,
            Principal principal) {

        String username = principal.getName();
        String message = invitationService.respondToInvitation(invitationId, response, username);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyInvitationForReservation(
            @RequestParam("reservationId") Long reservationId,
            Principal principal
    ) {
        String principalName = principal.getName(); // username/email según tu auth
        Long invitationId = invitationService.getMyInvitationIdForReservation(reservationId, principalName);

        if (invitationId == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(Map.of("id", invitationId));
    }


}

