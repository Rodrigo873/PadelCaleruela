package com.example.PadelCaleruela.dto;


import lombok.Data;

import java.util.List;
@Data
public class LeagueTeamRankingDTO {
    private Long teamId;
    private List<String> playerNames;
    private int matchesPlayed;
    private int matchesWon;
    private int matchesLost;
    private int points;

    public LeagueTeamRankingDTO(Long teamId, List<String> playerNames, int matchesPlayed,
                                int matchesWon, int matchesLost, int points) {
        this.teamId = teamId;
        this.playerNames = playerNames;
        this.matchesPlayed = matchesPlayed;
        this.matchesWon = matchesWon;
        this.matchesLost = matchesLost;
        this.points = points;
    }

    // Getters & Setters
}

