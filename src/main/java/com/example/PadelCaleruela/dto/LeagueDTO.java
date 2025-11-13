package com.example.PadelCaleruela.dto;


import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class LeagueDTO {
    private Long id;
    private String name;
    private String description;
    private Boolean isPublic;
    private String imageUrl;
    private LocalDate registrationDeadline;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Long creatorId;
    private Set<Long> playerIds;

}
