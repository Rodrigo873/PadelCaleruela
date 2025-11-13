package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.LeagueDTO;
import com.example.PadelCaleruela.dto.LeaguePairDTO;
import com.example.PadelCaleruela.service.LeagueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
@CrossOrigin(origins = "http://localhost:4200")
public class LeagueController {

    private final LeagueService leagueService;

    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    @PostMapping
    public LeagueDTO createLeague(@RequestBody LeagueDTO dto) {
        return leagueService.createLeague(dto);
    }

    @GetMapping("/public")
    public List<LeagueDTO> getPublicLeagues() {
        return leagueService.getAllPublicLeagues();
    }

    //🔹 Obtiene todas las ligas en las que participa un jugador específico
    @GetMapping("/player/{playerId}")
    public List<LeagueDTO> getLeaguesByPlayer(@PathVariable Long playerId) {
        return leagueService.getLeaguesByPlayer(playerId);
    }

    /** ✅ Obtener todas las ligas con status = "ACTIVE" */
    @GetMapping("/active")
    public List<LeagueDTO> getActiveLeagues() {
        return leagueService.getActiveLeagues();
    }

    @GetMapping("/user/{userId}/finished")
    public ResponseEntity<List<LeagueDTO>> getFinishedLeaguesByPlayer(@PathVariable Long userId) {
        List<LeagueDTO> finishedLeagues = leagueService.getFinishedLeaguesByPlayer(userId);
        return ResponseEntity.ok(finishedLeagues);
    }


    // 🆕 Obtener participantes por parejas
    @GetMapping("/{leagueId}/participants")
    public ResponseEntity<List<LeaguePairDTO>> getLeagueParticipantsGrouped(@PathVariable Long leagueId) {
        List<LeaguePairDTO> participants = leagueService.getLeagueParticipantsGrouped(leagueId);
        return ResponseEntity.ok(participants);
    }

    // 🔹 Nuevo endpoint
    @GetMapping("/{leagueId}/isUserInLeague/{userId}")
    public ResponseEntity<Boolean> isUserInLeague(
            @PathVariable Long leagueId,
            @PathVariable Long userId) {

        boolean isInLeague = leagueService.isUserInLeague(leagueId, userId);
        return ResponseEntity.ok(isInLeague);
    }

    @GetMapping("/{id}")
    public LeagueDTO getLeague(@PathVariable Long id) {
        return leagueService.getLeague(id);
    }

    @PostMapping("/{leagueId}/add-player/{playerId}")
    public void addPlayer(@PathVariable Long leagueId, @PathVariable Long playerId) {
        leagueService.addPlayerToLeague(leagueId, playerId);
    }

    @DeleteMapping("/{leagueId}/remove-player/{playerId}")
    public void removePlayer(@PathVariable Long leagueId, @PathVariable Long playerId) {
        leagueService.removePlayerFromLeague(leagueId, playerId);
    }

    @DeleteMapping("/{leagueId}/delete/{userId}")
    public ResponseEntity<Void> deleteLeague(@PathVariable Long leagueId, @PathVariable Long userId) {
        boolean deleted = leagueService.deleteLeague(leagueId, userId);
        if (deleted) {
            return ResponseEntity.ok().build(); // ✅ sin cuerpo
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }


}
