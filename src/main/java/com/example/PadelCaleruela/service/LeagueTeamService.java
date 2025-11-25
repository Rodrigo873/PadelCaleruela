package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueTeamDTO;
import com.example.PadelCaleruela.dto.PlayerInfoDTO;
import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.LeagueRepository;
import com.example.PadelCaleruela.repository.LeagueTeamRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class LeagueTeamService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    public LeagueTeamService(LeagueRepository leagueRepository,
                             LeagueTeamRepository teamRepository,
                             UserRepository userRepository,
                             AuthService authService) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    // ===================== HELPERS DE SEGURIDAD =====================

    /** Solo SUPERADMIN o ADMIN del mismo ayuntamiento, o el creador de la liga */
    private void ensureCanManageLeague(League league) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        // ADMIN → mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            return;
        }

        // USER → solo si es creador de la liga
        if (authService.isUser()) {
            if (league.getCreator() == null ||
                    !league.getCreator().getId().equals(current.getId())) {
                throw new AccessDeniedException("No puedes gestionar equipos de esta liga.");
            }
        }
    }

    /** Solo SUPERADMIN o ADMIN del mismo ayuntamiento, o el propio usuario */
    private void ensureCurrentIsPlayerOrAdmin(User player) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(player.getAyuntamiento());
            return;
        }

        if (authService.isUser() && !current.getId().equals(player.getId())) {
            throw new AccessDeniedException("No puedes gestionar equipos de otro jugador.");
        }
    }

    /** Para ver equipos de una liga (listado) */
    private void ensureCanViewLeagueTeams(League league) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        authService.ensureSameAyuntamiento(league.getAyuntamiento());

        boolean isCreator = league.getCreator() != null &&
                league.getCreator().getId().equals(current.getId());

        boolean isPlayerInLeague = league.getPlayers() != null &&
                league.getPlayers().stream().anyMatch(u -> u.getId().equals(current.getId()));

        // USER → puede ver si: liga pública, creador o participa
        if (authService.isUser()
                && !Boolean.TRUE.equals(league.getIsPublic())
                && !isCreator
                && !isPlayerInLeague) {
            throw new AccessDeniedException("No puedes ver los equipos de esta liga privada.");
        }

        // ADMIN ya está limitado por ayuntamiento
    }

    // ===================== CREAR EQUIPO =====================

    /** Crear una pareja manualmente */
    @Transactional
    public LeagueTeamDTO createTeam(Long leagueId, Long player1Id, Long player2Id) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));


        User player1 = userRepository.findById(player1Id)
                .orElseThrow(() -> new RuntimeException("Player 1 not found"));
        User player2 = userRepository.findById(player2Id)
                .orElseThrow(() -> new RuntimeException("Player 2 not found"));

        // Opcional: asegurar que los jugadores pertenecen al mismo ayuntamiento de la liga
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(player1.getAyuntamiento());
            authService.ensureSameAyuntamiento(player2.getAyuntamiento());
        }

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
                                false,
                                p.getStatus()
                        ))
                        .toList()
        );

        return dto;
    }

    // ===================== ABANDONAR EQUIPO =====================

    /** Permite a un jugador abandonar su equipo dentro de una liga */
    @Transactional
    public void leaveTeam(Long leagueId, Long playerId) {
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Jugador no encontrado"));

        // 🔐 Debe ser él mismo o ADMIN/SUPERADMIN del ayuntamiento
        ensureCurrentIsPlayerOrAdmin(player);

        List<LeagueTeam> teams = teamRepository.findByLeague_IdAndPlayers_Id(leagueId, playerId);

        if (teams.isEmpty()) {
            throw new RuntimeException("El jugador no pertenece a ningún equipo en esta liga.");
        }

        for (LeagueTeam team : teams) {
            Optional<User> playerOpt = team.getPlayers().stream()
                    .filter(p -> p.getId().equals(playerId))
                    .findFirst();

            if (playerOpt.isPresent()) {
                User p = playerOpt.get();
                team.getPlayers().remove(p);

                if (team.getPlayers().isEmpty()) {
                    teamRepository.delete(team);
                } else {
                    team.setName(team.getPlayers().stream()
                            .map(User::getUsername)
                            .reduce((a, b) -> a + " & " + b)
                            .orElse("Equipo sin nombre"));

                    teamRepository.save(team);
                }
            }
        }
    }

    // ===================== GENERAR PAREJAS RANDOM =====================

    /** Emparejar automáticamente los jugadores que no tengan pareja */
    @Transactional
    public List<LeagueTeam> generateRandomTeams(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // 🔐 Solo creador de liga / admin / superadmin
        ensureCanManageLeague(league);

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

        if (unpairedPlayers.size() % 2 != 0) {
            User last = unpairedPlayers.get(unpairedPlayers.size() - 1);
            System.out.println("⚠ Jugador sin pareja: " + last.getUsername());
        }

        return createdTeams;
    }

    // ===================== ELIMINAR EQUIPO =====================

    @Transactional
    public void deleteTeam(Long teamId) {
        LeagueTeam team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Equipo no encontrado"));

        League league = team.getLeague();
        if (league == null) {
            throw new RuntimeException("El equipo no tiene liga asociada.");
        }

        // 🔐 Solo creador de la liga / admin / superadmin
        ensureCanManageLeague(league);

        team.getPlayers().clear();

        if (team.getHomeMatches() != null) {
            team.getHomeMatches().clear();
        }
        if (team.getAwayMatches() != null) {
            team.getAwayMatches().clear();
        }

        teamRepository.delete(team);
    }

    // ===================== CONSULTAS =====================

    @Transactional(readOnly = true)
    public LeagueTeamDTO getTeamByUserAndLeague(Long leagueId, Long userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // 🔐 Ver que el actual puede ver la info de este usuario en esa liga
        var current = authService.getCurrentUser();

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());

            if (authService.isUser() && !current.getId().equals(userId)) {
                throw new AccessDeniedException("No puedes ver el equipo de otro jugador.");
            }
        }

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
                        .map(p -> new PlayerInfoDTO(p.getId(), p.getUsername(), p.getProfileImageUrl(), false,p.getStatus()))
                        .toList()
        );

        return dto;
    }

    public boolean isPlayerInTeam(Long leagueId, Long playerId) {
        return !teamRepository.findByLeague_IdAndPlayers_Id(leagueId, playerId).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<LeagueTeam> getTeamsByLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // 🔐 Control de acceso a ver equipos
        ensureCanViewLeagueTeams(league);

        return teamRepository.findByLeague(league);
    }
}
