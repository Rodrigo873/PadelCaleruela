package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueTeamRankingDTO;
import com.example.PadelCaleruela.dto.LeagueTeamRankingViewDTO;
import com.example.PadelCaleruela.dto.PlayerInfoDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeagueTeamRankingService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository teamRepository;
    private final LeagueTeamRankingRepository rankingRepository;
    private final LeagueMatchRepository matchRepository;
    private final AuthService authService;

    public LeagueTeamRankingService(LeagueRepository leagueRepository,
                                    LeagueTeamRepository teamRepository,
                                    LeagueTeamRankingRepository rankingRepository,
                                    LeagueMatchRepository matchRepository,
                                    AuthService authService) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.rankingRepository = rankingRepository;
        this.matchRepository = matchRepository;
        this.authService = authService;
    }

    // ============================================================
    // 🔐 SEGURIDAD
    // ============================================================

    private void ensureCanManageRanking(League league) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        // ADMIN ⇒ mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            return;
        }

        // USER ⇒ solo creador de la liga
        if (authService.isUser()) {
            if (league.getCreator() == null ||
                    !league.getCreator().getId().equals(current.getId())) {
                throw new AccessDeniedException("No puedes gestionar el ranking de esta liga.");
            }
        }
    }

    private void ensureCanViewRanking(League league) {
        var current = authService.getCurrentUser();

        // SUPERADMIN ⇒ siempre
        if (authService.isSuperAdmin()) return;

        // ADMIN ⇒ mismo ayuntamiento
        authService.ensureSameAyuntamiento(league.getAyuntamiento());

        boolean isCreator = league.getCreator() != null &&
                league.getCreator().getId().equals(current.getId());

        boolean isPlayer = league.getPlayers().stream()
                .anyMatch(u -> u.getId().equals(current.getId()));

        // USER ⇒ puede ver solo si la liga es pública, creador o participa
        if (authService.isUser()
                && !Boolean.TRUE.equals(league.getIsPublic())
                && !isCreator
                && !isPlayer) {
            throw new AccessDeniedException("No puedes ver el ranking de esta liga privada.");
        }
    }

    // ============================================================
    // 🏆 INICIALIZAR RANKING
    // ============================================================

    @Transactional
    public void initializeTeamRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        ensureCanManageRanking(league);

        List<LeagueTeam> teams = teamRepository.findByLeague(league);

        for (LeagueTeam team : teams) {
            rankingRepository.findByLeagueAndTeam(league, team)
                    .orElseGet(() -> {
                        LeagueTeamRanking ranking = new LeagueTeamRanking();
                        ranking.setLeague(league);
                        ranking.setTeam(team);
                        ranking.setMatchesPlayed(0);
                        ranking.setMatchesWon(0);
                        ranking.setMatchesLost(0);
                        ranking.setPoints(0);
                        return rankingRepository.save(ranking);
                    });
        }
    }

    // ============================================================
    // 🔁 ACTUALIZAR DESPUÉS DE UN PARTIDO
    // ============================================================

    @Transactional
    public List<LeagueTeamRankingViewDTO> updateAfterMatch(Long matchId,
                                                           LeagueTeam team1,
                                                           LeagueTeam team2,
                                                           int score1,
                                                           int score2) {

        League league = team1.getLeague();

        ensureCanManageRanking(league);

        boolean team1Wins = score1 > score2;

        updateTeam(team1, league, team1Wins);
        updateTeam(team2, league, !team1Wins);

        return getRanking(league.getId());
    }

    private void updateTeam(LeagueTeam team, League league, boolean won) {
        LeagueTeamRanking ranking = rankingRepository.findByLeagueAndTeam(league, team)
                .orElseThrow(() ->
                        new RuntimeException("Ranking not found for team " + team.getId()));

        ranking.setMatchesPlayed(ranking.getMatchesPlayed() + 1);

        if (won) {
            ranking.setMatchesWon(ranking.getMatchesWon() + 1);
            ranking.setPoints(ranking.getPoints() + 3);
        } else {
            ranking.setMatchesLost(ranking.getMatchesLost() + 1);
        }

        rankingRepository.save(ranking);
    }

    // ============================================================
    // 📊 OBTENER RANKING
    // ============================================================

    @Transactional(readOnly = true)
    public List<LeagueTeamRankingViewDTO> getRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        ensureCanViewRanking(league);

        List<LeagueTeamRanking> rankings =
                rankingRepository.findByLeagueWithTeamAndPlayers(leagueId);

        return rankings.stream()
                .map(r -> new LeagueTeamRankingViewDTO(
                        r.getTeam().getId(),
                        r.getTeam().getName(),
                        r.getTeam().getPlayers().stream()
                                .map(p -> new PlayerInfoDTO(
                                        p.getId(),
                                        p.getUsername(),
                                        p.getProfileImageUrl(),
                                        false
                                ))
                                .toList(),
                        r.getMatchesPlayed(),
                        r.getMatchesWon(),
                        r.getMatchesLost(),
                        r.getPoints()
                ))
                .toList();
    }
}
