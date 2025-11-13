package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueRankingDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueMatchRepository;
import com.example.PadelCaleruela.repository.LeagueRankingRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeagueRankingService {

    private final LeagueRepository leagueRepository;
    private final LeagueRankingRepository rankingRepository;
    private final LeagueMatchRepository matchRepository;
    private final UserRepository userRepository;

    public LeagueRankingService(LeagueRepository leagueRepository, LeagueRankingRepository rankingRepository,
                                LeagueMatchRepository matchRepository, UserRepository userRepository) {
        this.leagueRepository = leagueRepository;
        this.rankingRepository = rankingRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
    }

    /** Inicializa todos los jugadores en la tabla de ranking al empezar la liga */
    @Transactional
    public void initializeLeagueRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        for (User player : league.getPlayers()) {
            if (rankingRepository.findByLeagueAndPlayer(league, player).isEmpty()) {
                LeagueRanking ranking = new LeagueRanking();
                ranking.setLeague(league);
                ranking.setPlayer(player);
                ranking.setPoints(0);
                ranking.setMatchesPlayed(0);
                ranking.setMatchesWon(0);
                ranking.setMatchesLost(0);
                rankingRepository.save(ranking);
            }
        }
    }

    /** Actualiza el ranking después de cada partido finalizado */
    @Transactional
    public void updateRankingAfterMatch(Long matchId) {
        LeagueMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if (match.getStatus() != MatchStatus.FINISHED) {
            return; // Solo actualizar si el partido terminó
        }

        League league = match.getLeague();

        if (match.getTeam1() == null || match.getTeam2() == null) {
            throw new RuntimeException("Teams not assigned to match");
        }

        // 🔹 Obtener los equipos y sus jugadores
        Set<User> team1Players = match.getTeam1().getPlayers();
        Set<User> team2Players = match.getTeam2().getPlayers();

        // 🔹 Determinar ganador
        boolean team1Wins = match.getTeam1Score() != null && match.getTeam2Score() != null
                && match.getTeam1Score() > match.getTeam2Score();
        boolean draw = match.getTeam1Score() != null && match.getTeam2Score() != null
                && match.getTeam1Score().equals(match.getTeam2Score());

        // 🔹 Puntos por resultado (puedes ajustar si hay empates)
        int team1Points = team1Wins ? 3 : (draw ? 1 : 0);
        int team2Points = !team1Wins ? (draw ? 1 : 3) : 0;

        // 🔹 Actualizar ranking de cada jugador
        updatePlayers(team1Players, league, team1Points, team1Wins, draw);
        updatePlayers(team2Players, league, team2Points, !team1Wins && !draw, draw);
    }
    private void updatePlayers(Set<User> players, League league, int pointsEarned, boolean won, boolean draw) {
        for (User player : players) {
            LeagueRanking ranking = rankingRepository
                    .findByLeagueAndPlayer(league, player)
                    .orElseGet(() -> {
                        LeagueRanking newRank = new LeagueRanking();
                        newRank.setLeague(league);
                        newRank.setPlayer(player);
                        newRank.setMatchesPlayed(0);
                        newRank.setMatchesWon(0);
                        newRank.setMatchesLost(0);
                        newRank.setPoints(0);
                        newRank.setSetsWon(0);
                        newRank.setSetsLost(0);
                        return rankingRepository.save(newRank);
                    });

            ranking.setMatchesPlayed(ranking.getMatchesPlayed() + 1);

            if (won) {
                ranking.setMatchesWon(ranking.getMatchesWon() + 1);
            } else if (!draw) {
                ranking.setMatchesLost(ranking.getMatchesLost() + 1);
            }

            ranking.setPoints(ranking.getPoints() + pointsEarned);
            rankingRepository.save(ranking);
        }
    }

    public List<LeagueRanking> getRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        return rankingRepository.findByLeagueOrderByPointsDescMatchesWonDesc(league);
    }

    /** Recalcula todo el ranking (por si hubo correcciones o reprogramaciones) */
    @Transactional
    public void recalculateRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // Reiniciar
        for (LeagueRanking r : rankingRepository.findByLeagueOrderByPointsDescMatchesWonDesc(league)) {
            r.setMatchesPlayed(0);
            r.setMatchesWon(0);
            r.setMatchesLost(0);
            r.setPoints(0);
        }

        // Recalcular desde los partidos
        List<LeagueMatch> matches = matchRepository.findByLeague(league);
        for (LeagueMatch m : matches) {
            if (m.getStatus() == MatchStatus.FINISHED) {
                updateRankingAfterMatch(m.getId());
            }
        }
    }

    public List<LeagueRankingDTO> getRankingDto(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        return rankingRepository.findByLeagueOrderByPointsDescMatchesWonDesc(league)
                .stream()
                .map(r -> new LeagueRankingDTO(
                        r.getPlayer().getId(),
                        r.getPlayer().getUsername(), // o getName() si tu User tiene otro campo
                        r.getMatchesPlayed(),
                        r.getMatchesWon(),
                        r.getMatchesLost(),
                        r.getPoints()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<LeagueRankingDTO> updateRankingAndReturn(Long matchId) {
        updateRankingAfterMatch(matchId);
        LeagueMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));
        return getRankingDto(match.getLeague().getId());
    }

}
