package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.dto.LeagueTeamDTO;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.service.LeagueTeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/league-teams")
@CrossOrigin(origins = "http://localhost:4200")
public class LeagueTeamController {

    private final LeagueTeamService teamService;

    public LeagueTeamController(LeagueTeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping("/create")
    public ResponseEntity<LeagueTeamDTO> createTeam(
            @RequestParam Long leagueId,
            @RequestParam Long player1Id,
            @RequestParam Long player2Id) {

        LeagueTeamDTO dto = teamService.createTeam(leagueId, player1Id, player2Id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/leave")
    public ResponseEntity<String> leaveTeam(
            @RequestParam Long leagueId,
            @RequestParam Long playerId) {
        teamService.leaveTeam(leagueId, playerId);
        return ResponseEntity.ok("Jugador ha abandonado su equipo correctamente.");
    }

    // 🗑️ Eliminar equipo
    @DeleteMapping("/{teamId}")
    public void deleteTeam(@PathVariable Long teamId) {
        teamService.deleteTeam(teamId);
    }

    @GetMapping("/by-user")
    public LeagueTeamDTO getTeamByUserAndLeague(@RequestParam Long leagueId,
                                                @RequestParam Long userId) {
        return teamService.getTeamByUserAndLeague(leagueId, userId);
    }

    @PostMapping("/{leagueId}/generate-random")
    public List<LeagueTeam> generateRandomTeams(@PathVariable Long leagueId) {
        return teamService.generateRandomTeams(leagueId);
    }

    @GetMapping("/{leagueId}")
    public List<LeagueTeam> getTeams(@PathVariable Long leagueId) {
        return teamService.getTeamsByLeague(leagueId);
    }
}
