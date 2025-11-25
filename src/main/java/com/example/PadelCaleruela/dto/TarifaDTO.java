package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class TarifaDTO {
    private Long id;
    private Long ayuntamientoId;
    private String precioBase;
}