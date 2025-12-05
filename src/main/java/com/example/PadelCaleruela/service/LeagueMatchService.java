package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.*;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeagueMatchService {

    private final LeagueMatchRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final LeagueRankingService leagueRankingService;
    private final LeagueTeamRepository teamRepository;
    private final LeagueTeamRankingService teamRankingService;
    private final AuthService authService;

    public LeagueMatchService(LeagueMatchRepository matchRepository,
                              LeagueRepository leagueRepository,
                              UserRepository userRepository,
                              LeagueRankingService leagueRankingService,
                              LeagueTeamRankingService teamRankingService,
                              LeagueTeamRepository teamRepository,
                              AuthService authService) {
        this.matchRepository = matchRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.leagueRankingService = leagueRankingService;
        this.teamRepository = teamRepository;
        this.teamRankingService = teamRankingService;
        this.authService = authService;
    }

    // 🆕 Crear partido de liga (solo creador de la liga, ADMIN de ese ayuntamiento o SUPERADMIN)
    @Transactional
    public LeagueMatchDTO createMatch(LeagueMatchDTO dto) {
        League league = leagueRepository.findById(dto.getLeagueId())
                .orElseThrow(() -> new RuntimeException("League not found"));

        User current = authService.getCurrentUser();

        // 🔐 Multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
        }

        boolean isCreator = league.getCreator() != null &&
                league.getCreator().getId().equals(current.getId());

        // USER → solo si es creador de la liga
        if (authService.isUser() && !isCreator) {
            throw new AccessDeniedException("No puedes crear partidos en una liga que no has creado.");
        }
        // ADMIN → ya está limitado por ayuntamiento, permitido
        // SUPERADMIN → permitido

        // 🔹 Buscar los equipos según los jugadores enviados
        Long player1Id = dto.getTeam1().get(0).getId();
        Long player2Id = dto.getTeam2().get(0).getId();

        LeagueTeam team1 = teamRepository.findByPlayerAndLeague(player1Id, league.getId())
                .orElseThrow(() -> new RuntimeException("Team 1 not found for player " + player1Id));

        LeagueTeam team2 = teamRepository.findByPlayerAndLeague(player2Id, league.getId())
                .orElseThrow(() -> new RuntimeException("Team 2 not found for player " + player2Id));

        LeagueMatch match = new LeagueMatch();
        match.setLeague(league);
        match.setTeam1(team1);
        match.setTeam2(team2);
        match.setScheduledDate(dto.getScheduledDate());
        match.setStatus(MatchStatus.SCHEDULED);

        LeagueMatch saved = matchRepository.save(match);

        return mapToDto(saved);
    }

    // 🔍 Partidos de una liga (visibles:
    // - SUPERADMIN → todos
    // - ADMIN → ligas de su ayuntamiento
    // - USER → si la liga es pública o participa en ella o es el creador)
    @Transactional(readOnly = true)
    public List<LeagueMatchDTO> getMatchesByLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());

            boolean isCreator = league.getCreator() != null &&
                    league.getCreator().getId().equals(current.getId());

            boolean isPlayer = league.getPlayers().stream()
                    .anyMatch(p -> p.getId().equals(current.getId()));

            if (authService.isUser() &&
                    !Boolean.TRUE.equals(league.getIsPublic()) &&
                    !isCreator && !isPlayer) {
                throw new AccessDeniedException("No puedes ver los partidos de esta liga privada.");
            }
        }

        return matchRepository.findByLeague(league)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeagueMatchDTO> getUpcomingMatches(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());

            boolean isCreator = league.getCreator() != null &&
                    league.getCreator().getId().equals(current.getId());
            boolean isPlayer = league.getPlayers().stream()
                    .anyMatch(p -> p.getId().equals(current.getId()));

            if (authService.isUser() &&
                    !Boolean.TRUE.equals(league.getIsPublic()) &&
                    !isCreator && !isPlayer) {
                throw new AccessDeniedException("No puedes ver los partidos de esta liga privada.");
            }
        }

        return matchRepository.findByLeagueAndStatus(league, MatchStatus.SCHEDULED)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeagueMatchDTO> getPastMatches(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());

            boolean isCreator = league.getCreator() != null &&
                    league.getCreator().getId().equals(current.getId());
            boolean isPlayer = league.getPlayers().stream()
                    .anyMatch(p -> p.getId().equals(current.getId()));

            if (authService.isUser() &&
                    !Boolean.TRUE.equals(league.getIsPublic()) &&
                    !isCreator && !isPlayer) {
                throw new AccessDeniedException("No puedes ver los partidos de esta liga privada.");
            }
        }

        return matchRepository.findByLeagueAndStatus(league, MatchStatus.FINISHED)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // ✅ Actualizar resultado + ranking ()
    @Transactional
    public Map<String, Object> updateResultAndRanking(
            Long matchId,
            List<Map<String, Integer>> sets
    ) {
        LeagueMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        User current = authService.getCurrentUser();
        // ======================================================
        // 🔐 AUTORIZACIÓN
        // ======================================================

        if (!authService.isSuperAdmin()) {

            // ADMIN → mismo ayuntamiento
            //if (authService.isAdmin()) {
              //  authService.ensureSameAyuntamiento(league.getAyuntamiento());
            //}

            // USER → solo si juega el partido
            if (authService.isUser() || authService.isAdmin()) {

                boolean isInTeam1 = match.getTeam1().getPlayers()
                        .stream()
                        .anyMatch(p -> p.getId().equals(current.getId()));

                boolean isInTeam2 = match.getTeam2().getPlayers()
                        .stream()
                        .anyMatch(p -> p.getId().equals(current.getId()));

                if (!isInTeam1 && !isInTeam2) {
                    throw new AccessDeniedException(
                            "No puedes modificar el resultado porque no participas en este partido."
                    );
                }
            }
        }

        // ======================================================
        // 🛠️ ACTUALIZACIÓN DE RESULTADOS
        // ======================================================

        LeagueTeam team1 = match.getTeam1();
        LeagueTeam team2 = match.getTeam2();

        if (team1 == null || team2 == null) {
            throw new RuntimeException("El partido no tiene equipos asignados correctamente");
        }

        // 🧹 Limpia sets antiguos
        match.getSets().clear();

        int team1SetsWon = 0;
        int team2SetsWon = 0;
        int setNumber = 1;

        for (Map<String, Integer> setData : sets) {
            Integer t1 = setData.get("team1Games");
            Integer t2 = setData.get("team2Games");

            if (t1 == null || t2 == null) continue;

            LeagueMatchSet set = new LeagueMatchSet();
            set.setMatch(match);
            set.setSetNumber(setNumber++);
            set.setTeam1Games(t1);
            set.setTeam2Games(t2);

            match.getSets().add(set);

            if (t1 > t2) team1SetsWon++;
            else if (t2 > t1) team2SetsWon++;
        }

        match.setTeam1Score(team1SetsWon);
        match.setTeam2Score(team2SetsWon);
        match.setStatus(MatchStatus.FINISHED);
        match.setPlayedDate(LocalDateTime.now());

        matchRepository.save(match);

        // 🔢 Actualizar ranking
        List<LeagueTeamRankingViewDTO> updatedRanking =
                teamRankingService.updateAfterMatch(
                        matchId, team1, team2, team1SetsWon, team2SetsWon
                );

        // 🏁 ¿La liga terminó?
        checkAndFinalizeLeague(match.getLeague());

        Map<String, Object> response = new HashMap<>();
        response.put("match", mapToViewDto(match));
        response.put("ranking", updatedRanking);
        System.out.println("LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL"+response);
        return response;
    }


    @Transactional
    private void checkAndFinalizeLeague(League league) {
        long totalMatches = matchRepository.countByLeague(league);
        long finishedMatches = matchRepository.countByLeagueAndStatus(league, MatchStatus.FINISHED);

        if (totalMatches > 0 && totalMatches == finishedMatches) {
            league.setStatus(LeagueStatus.FINISHED);
            league.setEndDate(LocalDate.now());
            leagueRepository.save(league);
            System.out.println("🏆 Liga '" + league.getName() + "' finalizada automáticamente ✅");
        }
    }

    // 🔁 Reprogramar partido (mismas reglas que para actualizar resultado)
    @Transactional
    public void rescheduleMatch(Long matchId, LocalDateTime newDate) {
        LeagueMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        League league = match.getLeague();
        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
        }

        boolean isCreator = league.getCreator() != null &&
                league.getCreator().getId().equals(current.getId());

        if (authService.isUser() && !isCreator) {
            throw new AccessDeniedException("No puedes reprogramar partidos en una liga que no has creado.");
        }

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
        dto.setJornada(match.getJornada());

        if (match.getTeam1() != null) {
            dto.setTeam1(
                    match.getTeam1().getPlayers().stream()
                            .map(p -> new PlayerInfoDTO(
                                    p.getId(),
                                    p.getUsername(),
                                    p.getProfileImageUrl(),
                                    false,
                                    p.getStatus()
                            ))
                            .toList()
            );
        }

        if (match.getTeam2() != null) {
            dto.setTeam2(
                    match.getTeam2().getPlayers().stream()
                            .map(p -> new PlayerInfoDTO(
                                    p.getId(),
                                    p.getUsername(),
                                    p.getProfileImageUrl(),
                                    false,
                                    p.getStatus()
                            ))
                            .toList()
            );
        }

        if (match.getSets() != null && !match.getSets().isEmpty()) {
            List<MatchSetDTO> setDtos = match.getSets().stream()
                    .map(s -> {
                        MatchSetDTO sdto = new MatchSetDTO();
                        sdto.setSetNumber(s.getSetNumber());
                        sdto.setTeam1Games(s.getTeam1Games());
                        sdto.setTeam2Games(s.getTeam2Games());
                        return sdto;
                    })
                    .toList();
            dto.setSets(setDtos);
        }

        return dto;
    }

    private LeagueMatchViewDTO mapToViewDto(LeagueMatch match) {
        LeagueMatchViewDTO dto = new LeagueMatchViewDTO();

        dto.setId(match.getId());
        dto.setLeagueId(match.getLeague().getId());
        dto.setLeagueName(match.getLeague().getName());

        if (match.getTeam1() != null) {
            dto.setTeam1Id(match.getTeam1().getId());
            dto.setTeam1Name(match.getTeam1().getName());
        }

        if (match.getTeam2() != null) {
            dto.setTeam2Id(match.getTeam2().getId());
            dto.setTeam2Name(match.getTeam2().getName());
        }

        dto.setTeam1Score(match.getTeam1Score());
        dto.setTeam2Score(match.getTeam2Score());
        dto.setStatus(match.getStatus());
        dto.setScheduledDate(match.getScheduledDate());
        dto.setPlayedDate(match.getPlayedDate());
        dto.setJornada(match.getJornada());

        return dto;
    }
}
