// LeagueTeamRankingViewDTO.java
package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class LeagueTeamRankingViewDTO {
    private Long teamId;
    private String teamName;              // 👈 añade nombre del equipo si lo tienes
    private List<PlayerInfoDTO> players;     // 👈 solo datos planos de jugadores
    private int matchesPlayed;
    private int matchesWon;
    private int matchesLost;
    private int points;
}
