package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO plano para evitar problemas de serialización con Hibernate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeagueTeamDTO {
    private Long id;
    private String name;
    private Long leagueId;
    private String leagueName;
    private List<PlayerInfoDTO> players;

}
