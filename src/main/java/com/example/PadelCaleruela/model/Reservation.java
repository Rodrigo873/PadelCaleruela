package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // Usuario que creó la reserva
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    // Jugadores invitados o añadidos
    @ManyToMany
    @JoinTable(
            name = "reservation_players",
            joinColumns = @JoinColumn(name = "reservation_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    private Set<User> jugadores = new HashSet<>();

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<Invitation> invitations = new HashSet<>();


    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL)
    @ToString.Exclude
    private Payment payment;

    @Column(nullable = false)
    private boolean paid = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(nullable = false)
    private boolean isPublic = false;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public boolean isFull() {
        return jugadores.size() >= 4;
    }

    public boolean isPlayerRejected(User user) {
        return invitations.stream()
                .anyMatch(inv ->
                        inv.getReceiver().getId().equals(user.getId()) &&
                                inv.getStatus() == InvitationStatus.REJECTED);
    }

}
