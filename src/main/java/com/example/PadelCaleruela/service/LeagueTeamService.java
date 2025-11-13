package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueTeamDTO;
import com.example.PadelCaleruela.dto.PlayerInfoDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class LeagueTeamService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository teamRepository;
    private final UserRepository userRepository;

    public LeagueTeamService(LeagueRepository leagueRepository,
                             LeagueTeamRepository teamRepository,
                             UserRepository userRepository) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    /** Crear una pareja manualmente */
    @Transactional
    public LeagueTeamDTO createTeam(Long leagueId, Long player1Id, Long player2Id) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        User player1 = userRepository.findById(player1Id)
                .orElseThrow(() -> new RuntimeException("Player 1 not found"));
        User player2 = userRepository.findById(player2Id)
                .orElseThrow(() -> new RuntimeException("Player 2 not found"));

        // ⚠️ Evitar duplicados
        if (isPlayerInTeam(leagueId, player1Id) || isPlayerInTeam(leagueId, player2Id)) {
            throw new RuntimeException("Uno de los jugadores ya pertenece a una pareja en esta liga.");
        }

        // 🏗️ Crear equipo
        LeagueTeam team = new LeagueTeam();
        team.setLeague(league);
        team.setName(player1.getUsername() + " & " + player2.getUsername());
        team.getPlayers().add(player1);
        team.getPlayers().add(player2);

        teamRepository.save(team);

        // 🧩 Mapear a DTO
        LeagueTeamDTO dto = new LeagueTeamDTO();
        dto.setId(team.getId());
        dto.setName(team.getName());
        dto.setLeagueId(league.getId());
        dto.setLeagueName(league.getName());
        dto.setPlayers(
                team.getPlayers().stream()
                        .map(p -> new PlayerInfoDTO(
                                p.getId(),
                                p.getUsername(),
                                p.getProfileImageUrl(),
                                false
                        ))
                        .toList()
        );

        return dto;
    }

    /** 🔹 Permite a un jugador abandonar su equipo dentro de una liga */
    @Transactional
    public void leaveTeam(Long leagueId, Long playerId) {
        // Buscar los equipos donde participa el jugador en esta liga
        List<LeagueTeam> teams = teamRepository.findByLeague_IdAndPlayers_Id(leagueId, playerId);

        if (teams.isEmpty()) {
            throw new RuntimeException("El jugador no pertenece a ningún equipo en esta liga.");
        }

        for (LeagueTeam team : teams) {
            Optional<User> playerOpt = team.getPlayers().stream()
                    .filter(p -> p.getId().equals(playerId))
                    .findFirst();

            if (playerOpt.isPresent()) {
                User player = playerOpt.get();
                team.getPlayers().remove(player);

                // Si ya no quedan jugadores, eliminar el equipo
                if (team.getPlayers().isEmpty()) {
                    teamRepository.delete(team);
                    System.out.println("🗑️ Equipo eliminado: " + team.getName());
                } else {
                    // Actualizar el nombre del equipo (por si cambió la composición)
                    team.setName(team.getPlayers().stream()
                            .map(User::getUsername)
                            .reduce((a, b) -> a + " & " + b)
                            .orElse("Equipo sin nombre"));

                    teamRepository.save(team);
                    System.out.println("👤 Jugador " + player.getUsername() + " abandonó el equipo " + team.getName());
                }
            }
        }
    }



    /** Emparejar automáticamente los jugadores que no tengan pareja */
    @Transactional
    public List<LeagueTeam> generateRandomTeams(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // Jugadores ya en parejas
        Set<User> pairedPlayers = teamRepository.findByLeague(league).stream()
                .flatMap(t -> t.getPlayers().stream())
                .collect(HashSet::new, Set::add, Set::addAll);

        // Jugadores libres
        List<User> unpairedPlayers = new ArrayList<>(
                league.getPlayers().stream()
                        .filter(p -> !pairedPlayers.contains(p))
                        .toList()
        );


        Collections.shuffle(unpairedPlayers);
        List<LeagueTeam> createdTeams = new ArrayList<>();

        for (int i = 0; i < unpairedPlayers.size() - 1; i += 2) {
            User player1 = unpairedPlayers.get(i);
            User player2 = unpairedPlayers.get(i + 1);

            LeagueTeam team = new LeagueTeam();
            team.setLeague(league);
            team.setName(player1.getUsername() + " & " + player2.getUsername());
            team.getPlayers().add(player1);
            team.getPlayers().add(player2);
            createdTeams.add(teamRepository.save(team));
        }

        // Si hay un jugador impar, queda libre (no genera pareja)
        if (unpairedPlayers.size() % 2 != 0) {
            User lastPlayer = unpairedPlayers.get(unpairedPlayers.size() - 1);
            System.out.println("⚠ Jugador sin pareja: " + lastPlayer.getUsername());
        }

        return createdTeams;
    }

    @Transactional
    public void deleteTeam(Long teamId) {
        LeagueTeam team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Equipo no encontrado"));

        // ⚠️ Limpiar relaciones antes de eliminar (evita errores de constraint)
        team.getPlayers().clear();

        // También puedes limpiar matches si los hay (según tu modelo)
        if (team.getHomeMatches() != null) {
            team.getHomeMatches().clear();
        }
        if (team.getAwayMatches() != null) {
            team.getAwayMatches().clear();
        }

        teamRepository.delete(team);
    }

    @Transactional(readOnly = true)
    public LeagueTeamDTO getTeamByUserAndLeague(Long leagueId, Long userId) {
        LeagueTeam team = teamRepository.findByLeague_IdAndPlayers_Id(leagueId, userId)
                .stream()
                .findFirst()
                .orElse(null);

        if (team == null) return null;

        LeagueTeamDTO dto = new LeagueTeamDTO();
        dto.setId(team.getId());
        dto.setName(team.getName());
        dto.setLeagueId(team.getLeague().getId());
        dto.setLeagueName(team.getLeague().getName());
        dto.setPlayers(
                team.getPlayers().stream()
                        .map(p -> new PlayerInfoDTO(p.getId(), p.getUsername(), p.getProfileImageUrl(),false))
                        .toList()
        );

        return dto;
    }


    public boolean isPlayerInTeam(Long leagueId, Long playerId) {
        return !teamRepository.findByLeague_IdAndPlayers_Id(leagueId, playerId).isEmpty();
    }

    public List<LeagueTeam> getTeamsByLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        return teamRepository.findByLeague(league);
    }
}
