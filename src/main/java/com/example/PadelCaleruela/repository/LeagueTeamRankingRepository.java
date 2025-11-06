package com.example.PadelCaleruela.repository;


import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.model.LeagueTeamRanking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeagueTeamRankingRepository extends JpaRepository<LeagueTeamRanking, Long> {
    List<LeagueTeamRanking> findByLeagueOrderByPointsDescMatchesWonDesc(League league);
    Optional<LeagueTeamRanking> findByLeagueAndTeam(League league, LeagueTeam team);
}
