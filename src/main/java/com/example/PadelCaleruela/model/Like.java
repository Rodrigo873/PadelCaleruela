package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "post_likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"})
)
@Data
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @ManyToOne(optional = false)
    private Post post;

    private LocalDateTime createdAt = LocalDateTime.now();

    // getters y setters
}
