package com.example.PadelCaleruela.model;


import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Table(name = "league_matches")
@EqualsAndHashCode(exclude = {"sets", "league", "team1", "team2"})
@ToString(exclude = {"sets", "league", "team1", "team2"})
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team1_id")
    private LeagueTeam team1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team2_id")
    private LeagueTeam team2;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LeagueMatchSet> sets = new HashSet<>();


    private Integer jornada;



    // --- Métodos de ayuda ---
    public boolean isCompleted() {
        return status == MatchStatus.FINISHED;
    }

    // Getters y Setters
}

