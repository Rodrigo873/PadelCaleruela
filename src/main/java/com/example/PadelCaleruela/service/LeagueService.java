package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.dto.LeagueDTO;
import com.example.PadelCaleruela.dto.LeaguePairDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;

    private final LeagueInvitationRepository leagueInvitationRepository;

    private final LeagueMatchRepository leagueMatchRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final LeagueRankingRepository leagueRankingRepository;
    private final LeagueTeamRankingRepository leagueTeamRankingRepository;

    public LeagueService(LeagueRepository leagueRepository, UserRepository userRepository,
                         LeagueInvitationRepository leagueInvitationRepository,LeagueMatchRepository matchRepository,
                         LeagueTeamRepository leagueTeamRepository,
                         LeagueTeamRankingRepository leagueTeamRankingRepository,
                         LeagueRankingRepository leagueRankingRepository) {
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.leagueMatchRepository=matchRepository;
        this.leagueInvitationRepository=leagueInvitationRepository;
        this.leagueTeamRepository=leagueTeamRepository;
        this.leagueTeamRankingRepository=leagueTeamRankingRepository;
        this.leagueRankingRepository=leagueRankingRepository;

    }

    @Transactional
    public LeagueDTO createLeague(LeagueDTO dto) {
        League league = new League();
        league.setName(dto.getName());
        league.setDescription(dto.getDescription());
        league.setIsPublic(dto.getIsPublic());
        league.setImageUrl(dto.getImageUrl());
        league.setRegistrationDeadline(dto.getRegistrationDeadline());
        league.setStartDate(dto.getStartDate());
        league.setEndDate(dto.getEndDate());
        league.setStatus(LeagueStatus.PENDING);

        User creator = userRepository.findById(dto.getCreatorId())
                .orElseThrow(() -> new RuntimeException("Creator not found"));
        league.setCreator(creator);

        leagueRepository.save(league);
        return mapToDto(league);
    }

    /**
     * 🔹 Devuelve todas las ligas en las que participa un jugador.
     */
    @Transactional(readOnly = true)
    public List<LeagueDTO> getLeaguesByPlayer(Long playerId) {
        List<League> leagues = leagueRepository.findAll();

        return leagues.stream()
                .filter(l -> {
                    // ❌ Excluir las ligas finalizadas
                    if (l.getStatus() == LeagueStatus.FINISHED) {
                        return false;
                    }

                    // ✅ Ligas creadas por el usuario
                    boolean isCreator = l.getCreator() != null && l.getCreator().getId().equals(playerId);

                    // ✅ Ligas en las que participa como jugador
                    Set<User> players = l.getPlayers();
                    boolean isPlayer = players != null &&
                            players.stream().anyMatch(p -> p.getId().equals(playerId));

                    // ✅ Incluir si es creador o jugador
                    return isCreator || isPlayer;
                })
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public List<LeagueDTO> getFinishedLeaguesByPlayer(Long playerId) {
        List<League> leagues = leagueRepository.findAll();

        return leagues.stream()
                .filter(l -> {
                    // ✅ Ligas finalizadas
                    boolean isFinished = l.getStatus() == LeagueStatus.FINISHED;

                    // ✅ Ligas creadas por el usuario
                    boolean isCreator = l.getCreator() != null && l.getCreator().getId().equals(playerId);

                    // ✅ Ligas donde el usuario participó
                    Set<User> players = l.getPlayers();
                    boolean isPlayer = players != null &&
                            players.stream().anyMatch(p -> p.getId().equals(playerId));

                    // ✅ Devuelve solo si la liga está finalizada y el usuario participó o la creó
                    return isFinished && (isCreator || isPlayer);
                })
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /** 🔥 Retorna todas las ligas con estado "ACTIVE" */
    public List<LeagueDTO> getActiveLeagues() {
        return leagueRepository.findAllActivePublicLeagues()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }


    // 🆕 Obtener jugadores agrupados por parejas
    @Transactional(readOnly = true)
    public List<LeaguePairDTO> getLeagueParticipantsGrouped(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("Liga no encontrada"));

        // 🔹 1. Obtener todos los equipos con sus jugadores
        List<LeagueTeam> teams = leagueTeamRepository.findByLeagueIdWithPlayers(leagueId);

        // 🔹 2. Crear DTOs para las parejas (equipos)
        List<LeaguePairDTO> pairs = new ArrayList<>();

        for (LeagueTeam team : teams) {
            List<User> players = new ArrayList<>(team.getPlayers());

            List<Long> playerIds = players.stream().map(User::getId).toList();
            List<String> usernames = players.stream().map(User::getUsername).toList();
            List<String> images = players.stream()
                    .map(u -> u.getProfileImageUrl() != null ? u.getProfileImageUrl()
                            : "https://ui-avatars.com/api/?name=" + u.getUsername())
                    .toList();

            pairs.add(new LeaguePairDTO(playerIds, usernames, images));
        }

        // 🔹 3. Detectar jugadores sin pareja (inscritos pero no en ningún equipo)
        Set<Long> playersInTeams = teams.stream()
                .flatMap(t -> t.getPlayers().stream().map(User::getId))
                .collect(Collectors.toSet());

        List<User> unpairedPlayers = league.getPlayers().stream()
                .filter(p -> !playersInTeams.contains(p.getId()))
                .toList();

        // 🔹 4. Agregar los que están sin pareja
        for (User p : unpairedPlayers) {
            pairs.add(new LeaguePairDTO(
                    List.of(p.getId()),
                    List.of(p.getUsername()),
                    List.of(p.getProfileImageUrl() != null
                            ? p.getProfileImageUrl()
                            : "https://ui-avatars.com/api/?name=" + p.getUsername())
            ));
        }

        return pairs;
    }



    public List<LeagueDTO> getAllPublicLeagues() {
        return leagueRepository.findAllPublicPendingLeagues()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isUserInLeague(Long leagueId, Long userId) {
        return leagueRepository.findById(leagueId)
                .map(league -> league.getPlayers()
                        .stream()
                        .anyMatch(user -> user.getId().equals(userId)))
                .orElse(false);
    }


    public LeagueDTO getLeague(Long id) {
        return leagueRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("League not found"));
    }

    @Transactional
    public void addPlayerToLeague(Long leagueId, Long playerId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 🔹 Buscar si hay una invitación pendiente
        Optional<LeagueInvitation> existingInvitationOpt =
                leagueInvitationRepository.findByLeague_IdAndReceiver_IdAndStatus(leagueId, playerId, InvitationStatus.PENDING);

        if (existingInvitationOpt.isPresent()) {
            LeagueInvitation invitation = existingInvitationOpt.get();
            invitation.setStatus(InvitationStatus.ACCEPTED);
            leagueInvitationRepository.save(invitation);
        }

        // 🔹 Evitar duplicados (por si ya está en la liga)
        boolean alreadyInLeague = league.getPlayers().stream()
                .anyMatch(p -> p.getId().equals(playerId));

        if (!alreadyInLeague) {
            league.addPlayer(player);
            leagueRepository.save(league);
        }
    }


    @Transactional
    public void removePlayerFromLeague(Long leagueId, Long playerId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        league.removePlayer(player);
    }

    @Transactional
    public boolean deleteLeague(Long leagueId, Long userId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // 1️⃣ Borrar dependencias directas antes de la liga
        leagueInvitationRepository.deleteAllByLeague(league);
        leagueMatchRepository.deleteAllByLeague(league);
        leagueTeamRankingRepository.deleteAllByLeague(league);
        leagueRankingRepository.deleteAllByLeague(league);
        leagueTeamRepository.deleteAllByLeague(league);

        // 2️⃣ Borrar relaciones many-to-many (jugadores en liga)
        league.getPlayers().clear();
        leagueRepository.save(league); // sincroniza el cambio

        // ✅ Verificar que el usuario es el creador
        if (league.getCreator() != null && league.getCreator().getId().equals(userId)) {
            leagueRepository.delete(league);
            return true;
        }

        // ❌ No es el creador
        return false;
    }

    private LeagueDTO mapToDto(League league) {
        LeagueDTO dto = new LeagueDTO();
        dto.setId(league.getId());
        dto.setName(league.getName());
        dto.setDescription(league.getDescription());
        dto.setIsPublic(league.getIsPublic());
        dto.setImageUrl(league.getImageUrl());
        dto.setRegistrationDeadline(league.getRegistrationDeadline());
        dto.setStartDate(league.getStartDate());
        dto.setEndDate(league.getEndDate());
        dto.setStatus(league.getStatus().name());
        dto.setCreatorId(league.getCreator() != null ? league.getCreator().getId() : null);
        dto.setPlayerIds(league.getPlayers().stream().map(User::getId).collect(Collectors.toSet()));
        return dto;
    }
}

