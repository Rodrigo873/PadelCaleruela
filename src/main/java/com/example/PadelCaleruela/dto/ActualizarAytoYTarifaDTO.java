package com.example.PadelCaleruela.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ActualizarAytoYTarifaDTO {
    private Long id;
    private String nombre;
    private String codigoPostal;
    private Integer numeroPistas;
    private String stripeAccountId;
    private String telefono;
    private String email;
    private String imageUrl;
    private BigDecimal precioBase;
}
