package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AyuntamientoListDTO {
    private Long id;
    private String nombre;
    private String codigoPostal;
    private String imageUrl;
    private boolean activo;
    private Integer pistas;
}