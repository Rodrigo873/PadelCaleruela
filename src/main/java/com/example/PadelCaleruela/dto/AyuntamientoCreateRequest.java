package com.example.PadelCaleruela.dto;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AyuntamientoCreateRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El código postal es obligatorio")
    @Pattern(regexp = "^[0-9]{5}$", message = "Código postal inválido")
    private String codigoPostal;

    @NotNull(message = "El número de pistas es obligatorio")
    @Min(value = 1, message = "Debe haber al menos 1 pista")
    private Integer numeroPistas;

    @NotBlank(message = "El ID de Stripe es obligatorio")
    private String stripeAccountId;

    @NotBlank(message = "El teléfono es obligatorio")
    private String telefono;

    @Email(message = "Email inválido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    // 🔥 nueva propiedad
    @NotNull(message = "El precio base es obligatorio")
    private BigDecimal precioBase;

    private String immagenUrl;


    // 🔥 franjas opcionales
    private List<TarifaFranjaDTO> franjas;
}
