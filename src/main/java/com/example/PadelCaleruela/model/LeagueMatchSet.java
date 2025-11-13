package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Data
@Table(name = "league_match_sets")
@EqualsAndHashCode(exclude = {"match"})
@ToString(exclude = {"match"})
public class LeagueMatchSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private LeagueMatch match;

    private Integer setNumber; // 1, 2 o 3
    private Integer team1Games;
    private Integer team2Games;
}
