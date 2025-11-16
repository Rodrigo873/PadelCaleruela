package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
public class Ayuntamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private String codigoPostal;

    private Integer numeroPistas;

    private String stripeAccountId;

    private String telefono;

    private String email;

    private String imageUrl;

    // Relación con usuarios
    @OneToMany(mappedBy = "ayuntamiento")
    private List<User> usuarios;

}
