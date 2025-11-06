package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueMatch;
import com.example.PadelCaleruela.model.MatchStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.LeagueMatchRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
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

    public LeagueSchedulerService(LeagueRepository leagueRepository, LeagueMatchRepository matchRepository) {
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
    }

    @Transactional
    public List<LeagueMatch> generateMatchesForLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        List<User> players = new ArrayList<>(league.getPlayers());

        if (players.size() < 4) {
            throw new RuntimeException("Se necesitan al menos 4 jugadores para generar una liga (2 parejas).");
        }

        // Crear todas las combinaciones posibles de parejas (2 jugadores)
        List<Set<User>> pairs = generatePairs(players);

        // Crear todos los enfrentamientos posibles entre parejas distintas
        List<Pair<Set<User>, Set<User>>> matchups = generateMatchups(pairs);

        // Distribuir fechas orientativas entre la fecha de inicio y fin de la liga
        LocalDateTime start = league.getStartDate().atTime(18, 0);
        LocalDateTime end = league.getEndDate() != null
                ? league.getEndDate().atTime(18, 0)
                : start.plusWeeks(matchups.size());
        long totalDays = ChronoUnit.DAYS.between(start, end);
        long spacing = Math.max(totalDays / matchups.size(), 2); // mínimo 2 días entre partidos

        List<LeagueMatch> createdMatches = new ArrayList<>();
        int i = 0;

        for (Pair<Set<User>, Set<User>> matchup : matchups) {
            LeagueMatch match = new LeagueMatch();
            match.setLeague(league);
            match.setTeam1Players(matchup.getFirst());
            match.setTeam2Players(matchup.getSecond());
            match.setStatus(MatchStatus.SCHEDULED);

            // Fecha orientativa
            match.setScheduledDate(start.plusDays(i * spacing));

            matchRepository.save(match);
            createdMatches.add(match);
            i++;
        }

        return createdMatches;
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

    // Clase simple para manejar pares genéricos
    private static class Pair<A, B> {
        private final A first;
        private final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public A getFirst() { return first; }
        public B getSecond() { return second; }
    }
}
