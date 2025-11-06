package com.example.PadelCaleruela.dto;


import lombok.Data;

import java.time.LocalDateTime;
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
    private Set<Long> team1PlayerIds;
    private Set<Long> team2PlayerIds;

}
