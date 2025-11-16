package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Data
public class Invitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    @ToString.Exclude
    private User receiver;

    @Enumerated(EnumType.STRING)
    private InvitationStatus status; // PENDING, ACCEPTED, REJECTED

    @ManyToOne
    @JoinColumn(name = "ayuntamiento_id")
    private Ayuntamiento ayuntamiento;


    private LocalDateTime createdAt = LocalDateTime.now();
}
