package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HourSlotDTO {
    private LocalTime time;
    private String status;
    private boolean isPublic;
    private List<PlayerInfoDTO> players;
    private boolean esCreador;
    private Long reservationId;
    private BigDecimal precio;
    private Long pistaId;
    private String pistaNombre;

}
