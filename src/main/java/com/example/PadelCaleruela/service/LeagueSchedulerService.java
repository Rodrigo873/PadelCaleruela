package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueMatchRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.LeagueTeamRepository;
import com.example.PadelCaleruela.util.Pair;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class LeagueSchedulerService {

    private final LeagueRepository leagueRepository;
    private final LeagueMatchRepository matchRepository;
    private final LeagueTeamRepository teamRepository;
    private final AuthService authService;

    public LeagueSchedulerService(LeagueRepository leagueRepository,
                                  LeagueMatchRepository matchRepository,
                                  LeagueTeamRepository teamRepository,
                                  AuthService authService) {
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
        this.authService = authService;
    }

    // ============================================================
    // 🔐 SEGURIDAD
    // ============================================================
    /**
     * Solo puede generar jornadas:
     *  - SUPERADMIN → cualquiera
     *  - ADMIN → solo ligas de su ayuntamiento
     *  - USER → solo si es creador de la liga
     */
    private void ensureCanSchedule(League league) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        // ADMIN → mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            return;
        }

        // USER → solo el creador de la liga
        if (authService.isUser()) {
            if (league.getCreator() == null ||
                    !league.getCreator().getId().equals(current.getId())) {
                throw new AccessDeniedException("No puedes generar los partidos de esta liga.");
            }
        }
    }

    // ============================================================
    // 🏟️ GENERAR PARTIDOS
    // ============================================================
    @Transactional
    public List<LeagueMatch> generateMatchesForLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // 🔐 Control de seguridad
        ensureCanSchedule(league);

        List<LeagueTeam> teams = teamRepository.findByLeague(league);
        if (teams.size() < 2) {
            throw new RuntimeException("Se necesitan al menos 2 equipos para generar partidos.");
        }

        // 🔄 Generar jornadas (ida + vuelta si el boolean es true)
        List<List<Pair<LeagueTeam, LeagueTeam>>> jornadas =
                generateRoundRobin(new ArrayList<>(teams), true);

        // Fecha base de la jornada 1
        LocalDateTime start = league.getStartDate() != null
                ? league.getStartDate().atTime(18, 0)          // día de inicio de liga a las 18:00
                : LocalDateTime.now().plusDays(1).withHour(18).withMinute(0);

        long spacingWeeks = 1; // 1 jornada por semana

        List<LeagueMatch> created = new ArrayList<>();

        for (int j = 0; j < jornadas.size(); j++) {
            List<Pair<LeagueTeam, LeagueTeam>> jornada = jornadas.get(j);

            // ⏱ Fecha base de esta jornada (J1 = semana 0, J2 = +1 semana, etc.)
            LocalDateTime jornadaDate = start.plusWeeks(j * spacingWeeks);

            int indexInJornada = 0;

            for (Pair<LeagueTeam, LeagueTeam> matchPair : jornada) {
                LeagueMatch match = new LeagueMatch();
                match.setLeague(league);
                match.setTeam1(matchPair.getFirst());
                match.setTeam2(matchPair.getSecond());
                match.setStatus(MatchStatus.SCHEDULED);

                // 👉 Misma jornada para todos los partidos de este "j"
                match.setJornada(j + 1);

                // 👉 Dentro de la misma jornada, separados 2 horas entre partido y partido
                LocalDateTime matchDate = jornadaDate.plusHours(indexInJornada * 2L);
                match.setScheduledDate(matchDate);

                created.add(matchRepository.save(match));
                indexInJornada++;
            }
        }

        return created;
    }


    // ============================================================
    // 🔁 ALGORITMO GENERADOR DE ROUND-ROBIN (IDA Y VUELTA)
    // ============================================================
    private List<List<Pair<LeagueTeam, LeagueTeam>>> generateRoundRobin(
            List<LeagueTeam> teams, boolean includeReturnLegs
    ) {
        int numTeams = teams.size();
        boolean odd = (numTeams % 2 != 0);

        if (odd) {
            teams.add(null); // equipo fantasma
            numTeams++;
        }

        List<List<Pair<LeagueTeam, LeagueTeam>>> jornadas = new ArrayList<>();
        int numRounds = numTeams - 1;

        for (int round = 0; round < numRounds; round++) {
            List<Pair<LeagueTeam, LeagueTeam>> matches = new ArrayList<>();

            for (int i = 0; i < numTeams / 2; i++) {
                LeagueTeam home = teams.get(i);
                LeagueTeam away = teams.get(numTeams - 1 - i);

                if (home != null && away != null) {
                    matches.add(Pair.of(home, away));
                }
            }

            jornadas.add(matches);

            // Rotación
            LeagueTeam fixed = teams.get(0);
            List<LeagueTeam> rotating = new ArrayList<>(teams.subList(1, numTeams));
            Collections.rotate(rotating, 1);

            teams = new ArrayList<>();
            teams.add(fixed);
            teams.addAll(rotating);
        }

        // Vuelta
        if (includeReturnLegs) {
            List<List<Pair<LeagueTeam, LeagueTeam>>> vuelta = jornadas.stream()
                    .map(round ->
                            round.stream()
                                    .map(p -> Pair.of(p.getSecond(), p.getFirst()))
                                    .toList()
                    ).toList();

            jornadas.addAll(vuelta);
        }

        return jornadas;
    }
}
