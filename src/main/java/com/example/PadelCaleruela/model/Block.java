package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // quién bloquea
    @ManyToOne
    @JoinColumn(name = "blocked_by_user_id")
    private User blockedByUser;       // opcional

    @ManyToOne
    @JoinColumn(name = "blocked_by_ayuntamiento_id")
    private Ayuntamiento blockedByAyuntamiento;  // opcional

    // quién es bloqueado
    @ManyToOne(optional = false)
    @JoinColumn(name = "blocked_user_id")
    private User blockedUser;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
