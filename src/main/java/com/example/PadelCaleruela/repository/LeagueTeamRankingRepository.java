package com.example.PadelCaleruela.repository;


import com.example.PadelCaleruela.dto.LeagueTeamRankingViewDTO;
import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.model.LeagueTeamRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeagueTeamRankingRepository extends JpaRepository<LeagueTeamRanking, Long> {
    void deleteAllByLeague(League league);
    // LeagueTeamRankingRepository.java
    @Query("""
       select distinct r
       from LeagueTeamRanking r
       join fetch r.team t
       left join fetch t.players p
       where r.league = :league
       order by r.points desc, r.matchesWon desc
       """)
    List<LeagueTeamRanking> findRankingWithTeamAndPlayers(@Param("league") League league);

    @Query("""
    select distinct r
    from LeagueTeamRanking r
    join fetch r.team t
    join fetch t.players p
    where r.league.id = :leagueId
""")
    List<LeagueTeamRanking> findByLeagueWithTeamAndPlayers(@Param("leagueId") Long leagueId);

    List<LeagueTeamRanking> findByLeagueOrderByPointsDescMatchesWonDesc(League league);
    Optional<LeagueTeamRanking> findByLeagueAndTeam(League league, LeagueTeam team);
}
