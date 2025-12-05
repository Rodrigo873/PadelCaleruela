package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "lock_passwords")
@Data
public class LockPassword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 Relación con la cerradura (Lock)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lock_id", nullable = false)
    private Lock lock;

    // 🔗 Relación con la reserva asociada
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
