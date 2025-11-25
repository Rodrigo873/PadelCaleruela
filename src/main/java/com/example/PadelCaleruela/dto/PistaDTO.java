package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class PistaDTO {
    private Long id;
    private Long ayuntamientoId;
    private String nombre;
    private boolean activa;
}
