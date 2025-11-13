package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueTeam;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LeagueTeamRepository extends JpaRepository<LeagueTeam, Long> {
    void deleteAllByLeague(League league);

    List<LeagueTeam> findByLeague(League league);
    List<LeagueTeam> findByLeague_IdAndPlayers_Id(Long leagueId, Long playerId);

    @Query("""
    SELECT t FROM LeagueTeam t
    JOIN t.players p
    WHERE t.league.id = :leagueId
      AND p.id IN :playerIds
    GROUP BY t
    HAVING COUNT(p) = :size
""")
    Optional<LeagueTeam> findTeamByLeagueAndPlayerIds(
            @Param("leagueId") Long leagueId,
            @Param("playerIds") List<Long> playerIds,
            @Param("size") long size
    );

    // Proyección plana para agrupar luego por teamId
    interface TeamPlayerRow {
        Long getTeamId();
        Long getUserId();
        String getUsername();
        String getProfileImageUrl();
    }

    @Query("""
    select t from LeagueTeam t
    join t.players p
    where p.id = :playerId and t.league.id = :leagueId
""")
    Optional<LeagueTeam> findByPlayerAndLeague(@Param("playerId") Long playerId, @Param("leagueId") Long leagueId);

    @Query("""
    select distinct t from LeagueTeam t
    join fetch t.players
    where t.league.id = :leagueId
""")
    List<LeagueTeam> findByLeagueIdWithPlayers(@Param("leagueId") Long leagueId);



}
