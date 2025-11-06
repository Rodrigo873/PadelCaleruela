package com.example.PadelCaleruela.scheduler;


import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueStatus;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.service.LeagueCompletionService;
import com.example.PadelCaleruela.service.LeagueStartupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class LeagueAutoStarter {

    private final LeagueRepository leagueRepository;
    private final LeagueStartupService startupService;
    private final LeagueCompletionService completionService;

    public LeagueAutoStarter(LeagueRepository leagueRepository, LeagueStartupService startupService,
                             LeagueCompletionService completionService) {
        this.leagueRepository = leagueRepository;
        this.startupService = startupService;
        this.completionService=completionService;
    }

    /**
     * Revisa cada día a las 03:00 las ligas pendientes que deben comenzar.
     */
    @Scheduled(cron = "0 0 3 * * *") // cada día a las 3:00 AM
    public void checkAndStartLeagues() {
        List<League> pendingLeagues = leagueRepository.findAll().stream()
                .filter(l -> l.getStatus() == LeagueStatus.PENDING)
                .filter(l -> l.getStartDate() != null && !l.getStartDate().isAfter(LocalDate.now()))
                .toList();

        for (League league : pendingLeagues) {
            try {
                startupService.startLeague(league.getId());
            } catch (Exception e) {
                System.err.println("⚠️ Error al iniciar liga " + league.getName() + ": " + e.getMessage());
            }
        }
    }

    // dentro de LeagueAutoStarter.java

    @Scheduled(cron = "0 0 4 * * *") // todos los días a las 4:00 AM
    public void checkAndFinishLeagues() {
        List<League> activeLeagues = leagueRepository.findAll().stream()
                .filter(l -> l.getStatus() == LeagueStatus.ACTIVE)
                .toList();

        for (League league : activeLeagues) {
            try {
                completionService.checkAndCompleteLeague(league.getId());
            } catch (Exception e) {
                System.err.println("⚠️ Error al finalizar liga " + league.getName() + ": " + e.getMessage());
            }
        }
    }

}
