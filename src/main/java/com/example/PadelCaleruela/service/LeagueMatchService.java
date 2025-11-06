package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueMatchDTO;
import com.example.PadelCaleruela.dto.LeagueRankingDTO;
import com.example.PadelCaleruela.dto.LeagueTeamRankingDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LeagueMatchService {

    private final LeagueMatchRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final LeagueRankingService leagueRankingService;
    private final LeagueTeamRepository teamRepository;
    private final LeagueTeamRankingService teamRankingService;

    public LeagueMatchService(LeagueMatchRepository matchRepository, LeagueRepository leagueRepository,
                              UserRepository userRepository,LeagueRankingService leagueRankingService,
                              LeagueTeamRankingService teamRankingService,LeagueTeamRepository teamRepository) {
        this.matchRepository = matchRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.leagueRankingService=leagueRankingService;
        this.teamRepository=teamRepository;
        this.teamRankingService=teamRankingService;
    }

    @Transactional
    public LeagueMatchDTO createMatch(LeagueMatchDTO dto) {
        League league = leagueRepository.findById(dto.getLeagueId())
                .orElseThrow(() -> new RuntimeException("League not found"));

        LeagueMatch match = new LeagueMatch();
        match.setLeague(league);
        match.setScheduledDate(dto.getScheduledDate());
        match.setStatus(MatchStatus.SCHEDULED);

        match.setTeam1Players(dto.getTeam1PlayerIds().stream()
                .map(id -> userRepository.findById(id).orElseThrow())
                .collect(Collectors.toSet()));

        match.setTeam2Players(dto.getTeam2PlayerIds().stream()
                .map(id -> userRepository.findById(id).orElseThrow())
                .collect(Collectors.toSet()));

        matchRepository.save(match);
        return mapToDto(match);
    }

    public List<LeagueMatchDTO> getMatchesByLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        return matchRepository.findByLeague(league)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<LeagueMatchDTO> getUpcomingMatches(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        return matchRepository.findByLeagueAndScheduledDateAfter(league, LocalDateTime.now())
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<LeagueMatchDTO> getPastMatches(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        return matchRepository.findByLeagueAndScheduledDateBefore(league, LocalDateTime.now())
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> updateResultAndRanking(Long matchId, Integer team1Score, Integer team2Score) {
        LeagueMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        match.setTeam1Score(team1Score);
        match.setTeam2Score(team2Score);
        match.setStatus(MatchStatus.FINISHED);
        match.setPlayedDate(LocalDateTime.now());
        matchRepository.save(match);

        // Convertir sets de jugadores a equipos
        LeagueTeam team1 = teamRepository.findAll().stream()
                .filter(t -> t.getPlayers().containsAll(match.getTeam1Players()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Team1 not found"));
        LeagueTeam team2 = teamRepository.findAll().stream()
                .filter(t -> t.getPlayers().containsAll(match.getTeam2Players()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Team2 not found"));

        List<LeagueTeamRankingDTO> updatedRanking =
                teamRankingService.updateAfterMatch(matchId, team1, team2, team1Score, team2Score);

        Map<String, Object> response = new HashMap<>();
        response.put("match", mapToDto(match));
        response.put("ranking", updatedRanking);

        return response;
    }


    @Transactional
    public void rescheduleMatch(Long matchId, LocalDateTime newDate) {
        LeagueMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));
        match.setScheduledDate(newDate);
        match.setStatus(MatchStatus.POSTPONED);
        matchRepository.save(match);
    }

    private LeagueMatchDTO mapToDto(LeagueMatch match) {
        LeagueMatchDTO dto = new LeagueMatchDTO();
        dto.setId(match.getId());
        dto.setLeagueId(match.getLeague().getId());
        dto.setScheduledDate(match.getScheduledDate());
        dto.setPlayedDate(match.getPlayedDate());
        dto.setStatus(match.getStatus().name());
        dto.setTeam1Score(match.getTeam1Score());
        dto.setTeam2Score(match.getTeam2Score());
        dto.setTeam1PlayerIds(match.getTeam1Players().stream().map(User::getId).collect(Collectors.toSet()));
        dto.setTeam2PlayerIds(match.getTeam2Players().stream().map(User::getId).collect(Collectors.toSet()));
        return dto;
    }
}
