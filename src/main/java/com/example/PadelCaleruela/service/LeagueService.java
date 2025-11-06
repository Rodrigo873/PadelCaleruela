package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.dto.LeagueDTO;
import com.example.PadelCaleruela.dto.LeaguePairDTO;
import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;

    public LeagueService(LeagueRepository leagueRepository, UserRepository userRepository) {
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public LeagueDTO createLeague(LeagueDTO dto) {
        League league = new League();
        league.setName(dto.getName());
        league.setDescription(dto.getDescription());
        league.setPublic(dto.isPublic());
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
    @Transactional
    public List<LeagueDTO> getLeaguesByPlayer(Long playerId) {
        List<League> leagues = leagueRepository.findAll();

        return leagues.stream()
                .filter(l -> {
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

    /** 🔥 Retorna todas las ligas con estado "ACTIVE" */
    public List<LeagueDTO> getActiveLeagues() {
        return leagueRepository.findAll()
                .stream()
                .filter(l -> "ACTIVE".equalsIgnoreCase(String.valueOf(l.getStatus())))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // 🆕 Obtener jugadores agrupados por parejas
    public List<LeaguePairDTO> getLeagueParticipantsGrouped(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("Liga no encontrada"));

        List<User> players = new ArrayList<>(league.getPlayers());
        List<LeaguePairDTO> pairs = new ArrayList<>();

        for (int i = 0; i < players.size(); i += 2) {
            if (i + 1 < players.size()) {
                pairs.add(new LeaguePairDTO(
                        Arrays.asList(players.get(i).getId(), players.get(i + 1).getId()),
                        Arrays.asList(players.get(i).getUsername(), players.get(i + 1).getUsername()),
                        Arrays.asList(players.get(i).getProfileImageUrl(),players.get(i + 1).getProfileImageUrl())
                ));
            } else {
                pairs.add(new LeaguePairDTO(
                        Collections.singletonList(players.get(i).getId()),
                        Collections.singletonList(players.get(i).getUsername()),
                        Collections.singletonList(players.get(i).getProfileImageUrl())
                ));
            }
        }

        return pairs;
    }


    public List<LeagueDTO> getAllPublicLeagues() {
        return leagueRepository.findByIsPublicTrue()
                .stream().map(this::mapToDto).collect(Collectors.toList());
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
        league.addPlayer(player);
        leagueRepository.save(league);
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
        Optional<League> optLeague = leagueRepository.findById(leagueId);

        if (optLeague.isEmpty()) {
            return false;
        }

        League league = optLeague.get();

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
        dto.setPublic(league.isPublic());
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

