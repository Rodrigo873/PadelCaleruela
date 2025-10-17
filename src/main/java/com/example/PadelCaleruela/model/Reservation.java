package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que reserva la pista
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Fecha y hora de inicio
    private LocalDateTime startTime;

    // Fecha y hora de fin
    private LocalDateTime endTime;

    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL)
    private Payment payment;

    @Column(nullable = false)
    private boolean paid = false;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.PENDING; // 👈 nuevo

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
