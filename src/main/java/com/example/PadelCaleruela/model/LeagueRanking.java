package com.example.PadelCaleruela.model;


import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "league_rankings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"league_id", "player_id"}))
public class LeagueRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private User player;

    private int matchesPlayed;
    private int matchesWon;
    private int matchesLost;
    private int points; // Puntos de clasificación
    private int setsWon;
    private int setsLost;

    // Getters & Setters
}
