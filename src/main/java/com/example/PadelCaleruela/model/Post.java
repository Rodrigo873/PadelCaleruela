package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name="posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;
    private String matchResult;

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id")
    private User user;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
