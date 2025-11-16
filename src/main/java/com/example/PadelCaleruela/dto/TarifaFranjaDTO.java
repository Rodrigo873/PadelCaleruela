package com.example.PadelCaleruela.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TarifaFranjaDTO {
    @Min(0)  @Max(23)
    private int horaInicio;

    @Min(1)  @Max(24)
    private int horaFin;

    @NotNull
    private BigDecimal precio;
}
