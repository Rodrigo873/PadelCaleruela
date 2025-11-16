package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class AyuntamientoDTO {
    private Long id;
    private String nombre;
    private String codigoPostal;
    private Integer numeroPistas;
    private String stripeAccountId;
    private String telefono;
    private String email;
    private String imageUrl;
}
