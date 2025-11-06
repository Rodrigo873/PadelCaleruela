package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class LeagueRankingDTO {
    private Long playerId;
    private String playerName;
    private int matchesPlayed;
    private int matchesWon;
    private int matchesLost;
    private int points;

    // Constructor
    public LeagueRankingDTO(Long playerId, String playerName, int matchesPlayed, int matchesWon, int matchesLost, int points) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.matchesPlayed = matchesPlayed;
        this.matchesWon = matchesWon;
        this.matchesLost = matchesLost;
        this.points = points;
    }

    // Getters & Setters
}

