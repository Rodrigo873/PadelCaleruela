package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.model.LeagueRanking;
import com.example.PadelCaleruela.service.LeagueRankingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leagues/ranking")
@CrossOrigin(origins = "http://localhost:4200")
public class LeagueRankingController {

    private final LeagueRankingService rankingService;

    public LeagueRankingController(LeagueRankingService rankingService) {
        this.rankingService = rankingService;
    }

    @PostMapping("/{leagueId}/initialize")
    public void initialize(@PathVariable Long leagueId) {
        rankingService.initializeLeagueRanking(leagueId);
    }

    @GetMapping("/{leagueId}")
    public List<LeagueRanking> getRanking(@PathVariable Long leagueId) {
        return rankingService.getRanking(leagueId);
    }

    @PostMapping("/{matchId}/update")
    public void updateAfterMatch(@PathVariable Long matchId) {
        rankingService.updateRankingAfterMatch(matchId);
    }

    @PostMapping("/{leagueId}/recalculate")
    public void recalculate(@PathVariable Long leagueId) {
        rankingService.recalculateRanking(leagueId);
    }
}
