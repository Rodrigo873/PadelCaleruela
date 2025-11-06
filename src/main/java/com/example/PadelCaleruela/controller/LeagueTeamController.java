package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.service.LeagueTeamService;
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
    public LeagueTeam createTeam(@RequestParam Long leagueId,
                                 @RequestParam Long player1Id,
                                 @RequestParam Long player2Id) {
        return teamService.createTeam(leagueId, player1Id, player2Id);
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
