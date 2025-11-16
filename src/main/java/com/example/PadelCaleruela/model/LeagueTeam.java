package com.example.PadelCaleruela.model;


import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Table(name = "league_teams")
public class LeagueTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // opcional, puedes generarlo automáticamente

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    @ManyToMany
    @JoinTable(
            name = "league_team_players",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private Set<User> players = new HashSet<>();

    @OneToMany(mappedBy = "team1")
    private Set<LeagueMatch> homeMatches = new HashSet<>();

    @OneToMany(mappedBy = "team2")
    private Set<LeagueMatch> awayMatches = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "ayuntamiento_id")
    private Ayuntamiento ayuntamiento;


    // dentro de LeagueTeam.java
    public boolean isFull() {
        return players.size() >= 2;
    }

}
