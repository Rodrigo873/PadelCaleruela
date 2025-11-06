package com.example.PadelCaleruela.model;


import com.fasterxml.jackson.databind.deser.DataFormatReaders;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Data
@Table(name = "leagues")
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 1000)
    private String description;

    private boolean isPublic;

    private String imageUrl;

    private LocalDate registrationDeadline;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private boolean allowPlayerInvites = false;


    @Enumerated(EnumType.STRING)
    private LeagueStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToMany
    @JoinTable(
            name = "league_players",
            joinColumns = @JoinColumn(name = "league_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> players = new HashSet<>();

    // --- Relación con partidos ---
    @OneToMany(mappedBy = "league", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeagueMatch> matches = new ArrayList<>();


    // --- Métodos auxiliares ---
    public void addPlayer(User player) {
        players.add(player);
    }

    public void removePlayer(User player) {
        players.remove(player);
    }
}
