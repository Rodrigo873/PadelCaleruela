package com.example.PadelCaleruela.model;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Table(name = "league_matches")
public class LeagueMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Relación con la liga ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    private LocalDateTime scheduledDate;
    private LocalDateTime playedDate;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    private Integer team1Score;
    private Integer team2Score;

    // --- Equipos (cada equipo con 2 jugadores) ---
    @ManyToMany
    @JoinTable(
            name = "league_match_team1",
            joinColumns = @JoinColumn(name = "match_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private Set<User> team1Players = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "league_match_team2",
            joinColumns = @JoinColumn(name = "match_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private Set<User> team2Players = new HashSet<>();

    // --- Métodos de ayuda ---
    public boolean isCompleted() {
        return status == MatchStatus.FINISHED;
    }

    // Getters y Setters
}

