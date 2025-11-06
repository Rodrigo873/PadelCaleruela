package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueTeamRepository extends JpaRepository<LeagueTeam, Long> {
    List<LeagueTeam> findByLeague(League league);
    List<LeagueTeam> findByLeague_IdAndPlayers_Id(Long leagueId, Long playerId);


}
