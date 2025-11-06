package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueRanking;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeagueRankingRepository extends JpaRepository<LeagueRanking, Long> {
    List<LeagueRanking> findByLeagueOrderByPointsDescMatchesWonDesc(League league);
    Optional<LeagueRanking> findByLeagueAndPlayer(League league, User player);
}
