package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeagueMatchViewDTO {
    private Long id;
    private Long leagueId;
    private String leagueName;

    private Long team1Id;
    private String team1Name;

    private Long team2Id;
    private String team2Name;

    private Integer team1Score;
    private Integer team2Score;

    private MatchStatus status;
    private LocalDateTime scheduledDate;
    private LocalDateTime playedDate;

    private Integer jornada;

}
