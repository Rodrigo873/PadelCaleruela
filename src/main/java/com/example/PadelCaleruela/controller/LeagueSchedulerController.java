package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.model.LeagueMatch;
import com.example.PadelCaleruela.service.LeagueSchedulerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leagues/scheduler")
@CrossOrigin(origins = "http://localhost:4200")
public class LeagueSchedulerController {

    private final LeagueSchedulerService schedulerService;

    public LeagueSchedulerController(LeagueSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @PostMapping("/{leagueId}/generate")
    public List<LeagueMatch> generateMatches(@PathVariable Long leagueId) {
        return schedulerService.generateMatchesForLeague(leagueId);
    }
}
