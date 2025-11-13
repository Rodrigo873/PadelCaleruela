package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.dto.LeagueTeamRankingViewDTO;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.service.LeagueTeamRankingService;
import com.example.PadelCaleruela.service.LeagueTeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/league-team-ranking")
@CrossOrigin(origins = "http://localhost:4200")
public class LeagueTeamRankingController {

    private final LeagueTeamRankingService teamRankingService;

    public LeagueTeamRankingController(LeagueTeamRankingService teamRankingService) {
        this.teamRankingService=teamRankingService;
    }

    @GetMapping("/{leagueId}")
    public ResponseEntity<List<LeagueTeamRankingViewDTO>> getLeagueRanking(@PathVariable Long leagueId) {
        List<LeagueTeamRankingViewDTO> ranking = teamRankingService.getRanking(leagueId);
        return ResponseEntity.ok(ranking);
    }

}
