package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "pistas")
public class Pista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ayuntamiento_id")
    private Ayuntamiento ayuntamiento;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean activa = true;

}
