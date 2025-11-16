package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.LeagueTeamRepository;
import org.springframework.security.access.AccessDeniedException;
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
    private final AuthService authService;

    public LeagueStartupService(LeagueRepository leagueRepository,
                                LeagueTeamService teamService,
                                LeagueSchedulerService schedulerService,
                                LeagueTeamRankingService rankingService,
                                LeagueTeamRepository teamRepository,
                                AuthService authService) {
        this.leagueRepository = leagueRepository;
        this.teamService = teamService;
        this.schedulerService = schedulerService;
        this.rankingService = rankingService;
        this.teamRepository = teamRepository;
        this.authService = authService;
    }

    // ============================================================
    // 🔐 SEGURIDAD
    // ============================================================
    private void ensureCanStartLeague(League league) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        // ADMIN → mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            return;
        }

        // USER → solo creador
        if (authService.isUser()) {
            if (league.getCreator() == null ||
                    !league.getCreator().getId().equals(current.getId())) {
                throw new AccessDeniedException("No puedes iniciar esta liga.");
            }
        }
    }

    // ============================================================
    // 🏁 INICIAR LIGA
    // ============================================================
    @Transactional
    public void startLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // 🔐 Seguridad
        ensureCanStartLeague(league);

        if (league.getStatus() != LeagueStatus.PENDING) {
            throw new RuntimeException("La liga ya está iniciada o finalizada.");
        }

        // 1️⃣ Generar parejas aleatorias (si no hay equipos ya)
        List<LeagueTeam> randomTeams = teamService.generateRandomTeams(leagueId);
        System.out.println("✅ Parejas aleatorias creadas: " + randomTeams.size());

        // 2️⃣ Generar calendario de partidos
        List<LeagueMatch> matches = schedulerService.generateMatchesForLeague(leagueId);
        System.out.println("✅ Partidos generados: " + matches.size());

        // 3️⃣ Calcular fecha de fin estimada
        List<LeagueTeam> teams = teamRepository.findByLeague(league);
        int numTeams = teams.size();

        if (numTeams < 2) {
            throw new RuntimeException("No hay suficientes equipos para iniciar la liga.");
        }

        int jornadas = 2 * (numTeams - 1); // ida/vuelta
        LocalDate startDate = league.getStartDate() != null ? league.getStartDate() : LocalDate.now();
        LocalDate endDate = startDate.plusWeeks(jornadas);

        league.setEndDate(endDate);
        System.out.println("📅 Fecha estimada de fin: " + endDate);

        // 4️⃣ Inicializar ranking
        rankingService.initializeTeamRanking(leagueId);
        System.out.println("🏆 Ranking inicializado.");

        // 5️⃣ Activar liga
        league.setStatus(LeagueStatus.ACTIVE);
        leagueRepository.save(league);

        System.out.println("🚀 Liga " + league.getName() + " iniciada correctamente.");
    }
}
