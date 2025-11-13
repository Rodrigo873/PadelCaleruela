package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueMatch;
import com.example.PadelCaleruela.model.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LeagueMatchRepository extends JpaRepository<LeagueMatch, Long> {
    void deleteAllByLeague(League league);

    List<LeagueMatch> findByLeague(League league);
    List<LeagueMatch> findByLeagueAndStatus(League league, MatchStatus status);
    List<LeagueMatch> findByLeagueAndScheduledDateBefore(League league, LocalDateTime date);
    List<LeagueMatch> findByLeagueAndScheduledDateAfter(League league, LocalDateTime date);

    List<LeagueMatch> findByLeagueAndStatusAndScheduledDateAfter(
            League league,
            MatchStatus status,
            LocalDateTime date
    );

    long countByLeague(League league);
    long countByLeagueAndStatus(League league, MatchStatus status);

}
