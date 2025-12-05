package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

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

    @OneToMany(mappedBy = "pista")
    private Set<Lock> locks = new HashSet<>();

    @Column(nullable = false)
    private LocalTime apertura;

    @Column(nullable = false)
    private LocalTime cierre;



}
