package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "tarifa_franjas")
public class TarifaFranja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ayuntamiento_id")
    private Ayuntamiento ayuntamiento;

    @Column(nullable = false)
    private int horaInicio;  // 0-23

    @Column(nullable = false)
    private int horaFin;     // 0-23

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

}
