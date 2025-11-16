package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueRankingDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueMatchRepository;
import com.example.PadelCaleruela.repository.LeagueRankingRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
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
    private final AuthService authService;

    public LeagueRankingService(LeagueRepository leagueRepository,
                                LeagueRankingRepository rankingRepository,
                                LeagueMatchRepository matchRepository,
                                UserRepository userRepository,
                                AuthService authService) {
        this.leagueRepository = leagueRepository;
        this.rankingRepository = rankingRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    // ============================================================
    // 🔐 SEGURIDAD
    // ============================================================

    /** Para acciones de gestión del ranking (inicializar, recalcular, actualizar) */
    private void ensureCanManageRanking(League league) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            return;
        }

        // USER → solo el creador de la liga
        if (authService.isUser()) {
            if (league.getCreator() == null ||
                    !league.getCreator().getId().equals(current.getId())) {
                throw new AccessDeniedException("No puedes gestionar el ranking de esta liga.");
            }
        }
    }

    /** Para consultar ranking */
    private void ensureCanViewRanking(League league) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        // Debe ser del mismo ayuntamiento
        authService.ensureSameAyuntamiento(league.getAyuntamiento());

        // Si la liga es privada y el usuario no participa ni es creador → prohibido
        boolean isCreator = league.getCreator() != null &&
                league.getCreator().getId().equals(current.getId());

        boolean isPlayerInLeague = league.getPlayers().stream()
                .anyMatch(u -> u.getId().equals(current.getId()));

        if (!league.getIsPublic() && !isCreator && !isPlayerInLeague) {
            throw new AccessDeniedException("No puedes ver el ranking de una liga privada.");
        }
    }




    // ============================================================
    // 🎯 INICIALIZAR RANKING
    // ============================================================
    @Transactional
    public void initializeLeagueRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        ensureCanManageRanking(league);

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

    // ============================================================
    // 🎾 ACTUALIZAR TRAS PARTIDO
    // ============================================================
    @Transactional
    public void updateRankingAfterMatch(Long matchId) {
        LeagueMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        League league = match.getLeague();
        ensureCanManageRanking(league);

        if (match.getStatus() != MatchStatus.FINISHED) return;

        if (match.getTeam1() == null || match.getTeam2() == null) {
            throw new RuntimeException("Teams not assigned to match");
        }

        Set<User> team1Players = match.getTeam1().getPlayers();
        Set<User> team2Players = match.getTeam2().getPlayers();

        boolean team1Wins = match.getTeam1Score() != null &&
                match.getTeam2Score() != null &&
                match.getTeam1Score() > match.getTeam2Score();

        boolean draw = match.getTeam1Score() != null &&
                match.getTeam2Score() != null &&
                match.getTeam1Score().equals(match.getTeam2Score());

        int team1Points = team1Wins ? 3 : (draw ? 1 : 0);
        int team2Points = !team1Wins ? (draw ? 1 : 3) : 0;

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
                        return rankingRepository.save(newRank);
                    });

            ranking.setMatchesPlayed(ranking.getMatchesPlayed() + 1);

            if (won) ranking.setMatchesWon(ranking.getMatchesWon() + 1);
            else if (!draw) ranking.setMatchesLost(ranking.getMatchesLost() + 1);

            ranking.setPoints(ranking.getPoints() + pointsEarned);
            rankingRepository.save(ranking);
        }
    }


    // ============================================================
    // 📊 CONSULTAR RANKING
    // ============================================================
    public List<LeagueRanking> getRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        ensureCanViewRanking(league);

        return rankingRepository.findByLeagueOrderByPointsDescMatchesWonDesc(league);
    }

    // ============================================================
    // 🔄 RECALCULAR TODO
    // ============================================================
    @Transactional
    public void recalculateRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        ensureCanManageRanking(league);

        for (LeagueRanking r : rankingRepository.findByLeagueOrderByPointsDescMatchesWonDesc(league)) {
            r.setMatchesPlayed(0);
            r.setMatchesWon(0);
            r.setMatchesLost(0);
            r.setPoints(0);
        }

        List<LeagueMatch> matches = matchRepository.findByLeague(league);

        for (LeagueMatch m : matches) {
            if (m.getStatus() == MatchStatus.FINISHED) {
                updateRankingAfterMatch(m.getId());
            }
        }
    }

    // ============================================================
    // 📦 DTO COMPLETO
    // ============================================================
    public List<LeagueRankingDTO> getRankingDto(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        ensureCanViewRanking(league);

        return rankingRepository.findByLeagueOrderByPointsDescMatchesWonDesc(league)
                .stream()
                .map(r -> new LeagueRankingDTO(
                        r.getPlayer().getId(),
                        r.getPlayer().getUsername(),
                        r.getMatchesPlayed(),
                        r.getMatchesWon(),
                        r.getMatchesLost(),
                        r.getPoints()
                ))
                .toList();
    }

    @Transactional
    public List<LeagueRankingDTO> updateRankingAndReturn(Long matchId) {
        updateRankingAfterMatch(matchId);
        LeagueMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        return getRankingDto(match.getLeague().getId());
    }
}
