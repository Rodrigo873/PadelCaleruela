package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueMatch;
import com.example.PadelCaleruela.model.LeagueStatus;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.LeagueTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class LeagueStartupService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamService teamService;
    private final LeagueSchedulerService schedulerService;
    private final LeagueTeamRankingService rankingService;
    private final LeagueTeamRepository teamRepository;

    public LeagueStartupService(LeagueRepository leagueRepository,
                                LeagueTeamService teamService,
                                LeagueSchedulerService schedulerService,
                                LeagueTeamRankingService rankingService,
                                LeagueTeamRepository teamRepository) {
        this.leagueRepository = leagueRepository;
        this.teamService = teamService;
        this.schedulerService = schedulerService;
        this.rankingService = rankingService;
        this.teamRepository=teamRepository;
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

        // 1️⃣ Generar parejas aleatorias (si no existen)
        List<LeagueTeam> randomTeams = teamService.generateRandomTeams(leagueId);
        System.out.println("✅ Parejas aleatorias creadas: " + randomTeams.size());

        // 2️⃣ Generar partidos ida y vuelta
        List<LeagueMatch> matches = schedulerService.generateMatchesForLeague(leagueId);
        System.out.println("✅ Partidos generados: " + matches.size());

        // 3️⃣ Calcular fecha final de liga (una jornada por semana)
        List<LeagueTeam> teams = teamRepository.findByLeague(league);
        int numTeams = teams.size();

        if (numTeams < 2) {
            throw new RuntimeException("No hay suficientes equipos para iniciar la liga.");
        }

        int jornadas = 2 * (numTeams - 1); // ida y vuelta
        LocalDate startDate = league.getStartDate() != null ? league.getStartDate() : LocalDate.now();
        LocalDate endDate = startDate.plusWeeks(jornadas);

        league.setEndDate(endDate);
        System.out.println("📅 Fecha estimada de fin: " + endDate);

        // 4️⃣ Inicializar el ranking
        rankingService.initializeTeamRanking(leagueId);
        System.out.println("✅ Ranking inicializado.");

        // 5️⃣ Cambiar estado a ACTIVA y guardar
        league.setStatus(LeagueStatus.ACTIVE);
        leagueRepository.save(league);

        System.out.println("🏁 Liga " + league.getName() + " iniciada correctamente.");
    }

}
