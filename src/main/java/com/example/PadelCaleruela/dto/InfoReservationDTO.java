package com.example.PadelCaleruela.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
@Data
public class InfoReservationDTO {
    private Long id;
    private LocalDateTime startTime;
    private String status;
}
