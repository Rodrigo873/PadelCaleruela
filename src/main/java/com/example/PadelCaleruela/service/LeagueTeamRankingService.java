package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.dto.LeagueTeamRankingDTO;
import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.LeagueTeamRanking;
import com.example.PadelCaleruela.repository.LeagueMatchRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.LeagueTeamRankingRepository;
import com.example.PadelCaleruela.repository.LeagueTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LeagueTeamRankingService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository teamRepository;
    private final LeagueTeamRankingRepository rankingRepository;
    private final LeagueMatchRepository matchRepository;

    public LeagueTeamRankingService(LeagueRepository leagueRepository,
                                    LeagueTeamRepository teamRepository,
                                    LeagueTeamRankingRepository rankingRepository,
                                    LeagueMatchRepository matchRepository) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.rankingRepository = rankingRepository;
        this.matchRepository = matchRepository;
    }

    @Transactional
    public void initializeTeamRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        List<LeagueTeam> teams = teamRepository.findByLeague(league);
        for (LeagueTeam team : teams) {
            rankingRepository.findByLeagueAndTeam(league, team)
                    .or(() -> Optional.of(rankingRepository.save(new LeagueTeamRanking() {{
                        setLeague(league);
                        setTeam(team);
                        setMatchesPlayed(0);
                        setMatchesWon(0);
                        setMatchesLost(0);
                        setPoints(0);
                    }})));
        }
    }

    @Transactional
    public List<LeagueTeamRankingDTO> updateAfterMatch(Long matchId, LeagueTeam team1, LeagueTeam team2,
                                                       int score1, int score2) {
        League league = team1.getLeague();
        boolean team1Wins = score1 > score2;
        updateTeam(team1, league, team1Wins);
        updateTeam(team2, league, !team1Wins);
        return getRanking(league.getId());
    }

    private void updateTeam(LeagueTeam team, League league, boolean won) {
        LeagueTeamRanking ranking = rankingRepository.findByLeagueAndTeam(league, team)
                .orElseThrow(() -> new RuntimeException("Ranking not found for team " + team.getId()));

        ranking.setMatchesPlayed(ranking.getMatchesPlayed() + 1);
        if (won) {
            ranking.setMatchesWon(ranking.getMatchesWon() + 1);
            ranking.setPoints(ranking.getPoints() + 3);
        } else {
            ranking.setMatchesLost(ranking.getMatchesLost() + 1);
        }
        rankingRepository.save(ranking);
    }

    public List<LeagueTeamRankingDTO> getRanking(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        return rankingRepository.findByLeagueOrderByPointsDescMatchesWonDesc(league)
                .stream()
                .map(r -> new LeagueTeamRankingDTO(
                        r.getTeam().getId(),
                        r.getTeam().getPlayers().stream()
                                .map(User::getUsername)
                                .collect(Collectors.toList()),
                        r.getMatchesPlayed(),
                        r.getMatchesWon(),
                        r.getMatchesLost(),
                        r.getPoints()
                ))
                .collect(Collectors.toList());
    }
}
