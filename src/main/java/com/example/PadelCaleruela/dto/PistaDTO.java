package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PistaDTO {
    private Long id;
    private Long ayuntamientoId;
    private String nombre;
    private boolean activa;
    private String horaApertura;
    private String horaCierre;
}
