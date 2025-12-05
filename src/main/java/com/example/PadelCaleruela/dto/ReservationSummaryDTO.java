package com.example.PadelCaleruela.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReservationSummaryDTO {

    private Long reservationId;
    private Long pistaId;
    private String pistaNombre;


    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private List<PlayerSimpleDTO> jugadores;

}
