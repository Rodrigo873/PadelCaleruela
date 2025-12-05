package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PistaListDTO {
    private Long id;
    private String nombre;
    private boolean activa;
    private long numeroLocks;
    private String ayuntamientoImagen;
    private String ayuntamientoNombre;

}
