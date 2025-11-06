package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueMatch;
import com.example.PadelCaleruela.model.LeagueStatus;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.repository.LeagueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeagueStartupService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamService teamService;
    private final LeagueSchedulerService schedulerService;
    private final LeagueTeamRankingService rankingService;

    public LeagueStartupService(LeagueRepository leagueRepository,
                                LeagueTeamService teamService,
                                LeagueSchedulerService schedulerService,
                                LeagueTeamRankingService rankingService) {
        this.leagueRepository = leagueRepository;
        this.teamService = teamService;
        this.schedulerService = schedulerService;
        this.rankingService = rankingService;
    }

    /**
     * Inicia una liga completa: genera equipos, partidos y ranking.
     */
    @Transactional
    public void startLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        if (league.getStatus() != LeagueStatus.PENDING) {
            throw new RuntimeException("La liga ya está iniciada o finalizada.");
        }

        // 1️⃣ Generar parejas aleatorias para jugadores sin pareja
        List<LeagueTeam> randomTeams = teamService.generateRandomTeams(leagueId);
        System.out.println("✅ Parejas aleatorias creadas: " + randomTeams.size());

        // 2️⃣ Generar partidos según las parejas
        List<LeagueMatch> matches = schedulerService.generateMatchesForLeague(leagueId);
        System.out.println("✅ Partidos generados: " + matches.size());

        // 3️⃣ Inicializar el ranking por equipos
        rankingService.initializeTeamRanking(leagueId);
        System.out.println("✅ Ranking inicializado.");

        // 4️⃣ Cambiar estado de la liga a ACTIVA
        league.setStatus(LeagueStatus.ACTIVE);
        leagueRepository.save(league);
        System.out.println("🏁 Liga " + league.getName() + " iniciada correctamente.");
    }
}
