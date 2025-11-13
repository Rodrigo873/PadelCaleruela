package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueMatchRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.LeagueTeamRepository;
import com.example.PadelCaleruela.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeagueSchedulerService {

    private final LeagueRepository leagueRepository;
    private final LeagueMatchRepository matchRepository;
    private final LeagueTeamRepository teamRepository;

    public LeagueSchedulerService(LeagueRepository leagueRepository, LeagueMatchRepository matchRepository,
                                  LeagueTeamRepository leagueTeamRepository) {
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
        this.teamRepository=leagueTeamRepository;
    }

    @Transactional
    public List<LeagueMatch> generateMatchesForLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        List<LeagueTeam> teams = teamRepository.findByLeague(league);
        if (teams.size() < 2) {
            throw new RuntimeException("Se necesitan al menos 2 equipos para generar partidos.");
        }

        // 🔹 Generar calendario (ida y vuelta)
        List<List<Pair<LeagueTeam, LeagueTeam>>> jornadas = generateRoundRobin(teams, true); // true = con vuelta

        LocalDateTime start = league.getStartDate() != null
                ? league.getStartDate().atTime(18, 0)
                : LocalDateTime.now().plusDays(1).withHour(18).withMinute(0);

        long spacing = 7; // una jornada por semana

        List<LeagueMatch> created = new ArrayList<>();

        for (int j = 0; j < jornadas.size(); j++) {
            List<Pair<LeagueTeam, LeagueTeam>> jornada = jornadas.get(j);
            for (Pair<LeagueTeam, LeagueTeam> matchPair : jornada) {
                LeagueMatch match = new LeagueMatch();
                match.setLeague(league);
                match.setTeam1(matchPair.getFirst());
                match.setTeam2(matchPair.getSecond());
                match.setStatus(MatchStatus.SCHEDULED);
                match.setJornada(j + 1);
                match.setScheduledDate(start.plusDays(j * spacing));

                created.add(matchRepository.save(match));
            }
        }

        return created;
    }

    private List<List<Pair<LeagueTeam, LeagueTeam>>> generateRoundRobin(List<LeagueTeam> teams, boolean includeReturnLegs) {
        int numTeams = teams.size();
        boolean odd = (numTeams % 2 != 0);
        if (odd) {
            teams.add(null); // equipo "fantasma" para equilibrar
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

            // rotar equipos (excepto el primero)
            LeagueTeam fixed = teams.get(0);
            List<LeagueTeam> rotating = new ArrayList<>(teams.subList(1, teams.size()));
            Collections.rotate(rotating, 1);
            teams = new ArrayList<>();
            teams.add(fixed);
            teams.addAll(rotating);
        }

        // 🔁 Generar vuelta (ida y vuelta)
        if (includeReturnLegs) {
            List<List<Pair<LeagueTeam, LeagueTeam>>> vuelta = jornadas.stream()
                    .map(roundList -> roundList.stream()
                            .map(p -> Pair.of(p.getSecond(), p.getFirst()))
                            .toList())
                    .toList();
            jornadas.addAll(vuelta);
        }

        return jornadas;
    }


    /** Genera todos los emparejamientos únicos (round-robin simple) entre equipos. */
    private List<Pair<LeagueTeam, LeagueTeam>> generateTeamMatchups(List<LeagueTeam> teams) {
        List<Pair<LeagueTeam, LeagueTeam>> result = new ArrayList<>();
        int n = teams.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                result.add(new Pair<>(teams.get(i), teams.get(j)));
            }
        }
        return result;
    }

    private List<Set<User>> generatePairs(List<User> players) {
        List<Set<User>> pairs = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                Set<User> pair = new HashSet<>();
                pair.add(players.get(i));
                pair.add(players.get(j));
                pairs.add(pair);
            }
        }
        return pairs;
    }

    private List<Pair<Set<User>, Set<User>>> generateMatchups(List<Set<User>> pairs) {
        List<Pair<Set<User>, Set<User>>> matchups = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            for (int j = i + 1; j < pairs.size(); j++) {
                // Evitar que una pareja juegue contra sí misma o con jugadores repetidos
                if (Collections.disjoint(pairs.get(i), pairs.get(j))) {
                    matchups.add(new Pair<>(pairs.get(i), pairs.get(j)));
                }
            }
        }
        return matchups;
    }

}
