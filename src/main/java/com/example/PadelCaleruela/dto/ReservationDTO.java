package com.example.PadelCaleruela.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReservationDTO {
    private Long id;
    private Long userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
    private List<UserDTO> jugadores;
    private String mensaje;
    private boolean esCreador;
    private String status;
    @JsonProperty("isPublic")
    private boolean isPublic;
    private BigDecimal precio;



}
