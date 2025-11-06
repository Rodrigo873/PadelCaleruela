package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.service.LeagueStartupService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leagues/startup")
@CrossOrigin(origins = "http://localhost:4200")
public class LeagueStartupController {

    private final LeagueStartupService startupService;

    public LeagueStartupController(LeagueStartupService startupService) {
        this.startupService = startupService;
    }

    /** Iniciar manualmente una liga (por el admin o cron job) */
    @PostMapping("/{leagueId}/start")
    public String startLeague(@PathVariable Long leagueId) {
        startupService.startLeague(leagueId);
        return "Liga iniciada correctamente";
    }
}
