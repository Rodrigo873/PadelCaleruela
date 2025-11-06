package com.example.PadelCaleruela.model;


import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "league_team_rankings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"league_id", "team_id"}))
public class LeagueTeamRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private LeagueTeam team;

    private int matchesPlayed;
    private int matchesWon;
    private int matchesLost;
    private int points;
    private int gamesWon;
    private int gamesLost;

}
