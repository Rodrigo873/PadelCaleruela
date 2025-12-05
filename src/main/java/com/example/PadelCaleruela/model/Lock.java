package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "locks")
@Data
public class Lock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String deviceNumber; // ID de WeLock

    @Column(nullable = false)
    private String bleName; // Ej: WeLockCNJJJ

    @Column(nullable = false)
    private String deviceMAC; // ← NECESARIO

    // 🔗 Relación con la pista
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pista_id", nullable = false)
    private Pista pista;

}
