package com.example.PadelCaleruela.dto;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
public class LeagueMatchDTO {
    private Long id;
    private Long leagueId;
    private LocalDateTime scheduledDate;
    private LocalDateTime playedDate;
    private String status;
    private Integer team1Score;
    private Integer team2Score;
    private List<PlayerInfoDTO> team1;
    private List<PlayerInfoDTO> team2;
    private List<MatchSetDTO> sets;
    private Integer jornada;
}
