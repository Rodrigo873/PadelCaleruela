package com.example.PadelCaleruela.service;

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
    public LeagueTeam createTeam(Long leagueId, Long player1Id, Long player2Id) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        User player1 = userRepository.findById(player1Id)
                .orElseThrow(() -> new RuntimeException("Player 1 not found"));
        User player2 = userRepository.findById(player2Id)
                .orElseThrow(() -> new RuntimeException("Player 2 not found"));

        // Verificar que ninguno ya esté en otra pareja de la misma liga
        if (isPlayerInTeam(leagueId, player1Id) || isPlayerInTeam(leagueId, player2Id)) {
            throw new RuntimeException("Uno de los jugadores ya pertenece a una pareja en esta liga.");
        }

        LeagueTeam team = new LeagueTeam();
        team.setLeague(league);
        team.setName(player1.getUsername() + " & " + player2.getUsername());
        team.getPlayers().add(player1);
        team.getPlayers().add(player2);

        return teamRepository.save(team);
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
        List<User> unpairedPlayers = league.getPlayers().stream()
                .filter(p -> !pairedPlayers.contains(p))
                .toList();

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

    public boolean isPlayerInTeam(Long leagueId, Long playerId) {
        return !teamRepository.findByLeague_IdAndPlayers_Id(leagueId, playerId).isEmpty();
    }

    public List<LeagueTeam> getTeamsByLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        return teamRepository.findByLeague(league);
    }
}
