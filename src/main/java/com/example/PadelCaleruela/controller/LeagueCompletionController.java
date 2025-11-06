package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.service.LeagueCompletionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leagues/completion")
@CrossOrigin(origins = "http://localhost:4200")
public class LeagueCompletionController {

    private final LeagueCompletionService completionService;

    public LeagueCompletionController(LeagueCompletionService completionService) {
        this.completionService = completionService;
    }

    @PostMapping("/{leagueId}/check")
    public String checkAndComplete(@PathVariable Long leagueId) {
        completionService.checkAndCompleteLeague(leagueId);
        return "Comprobación de finalización ejecutada.";
    }
}
