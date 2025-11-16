package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "tarifas")
public class Tarifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ayuntamiento_id")
    private Ayuntamiento ayuntamiento;

    // Precio por defecto si no encaja en ningún tramo
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precioBase;

}
