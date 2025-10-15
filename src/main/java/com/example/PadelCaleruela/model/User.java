package com.example.PadelCaleruela.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Data
@Table(name="users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique=true, nullable=false)
    private String username;

    @Column(nullable=false)
    private String password;

    private String fullName;

    @Column(nullable=false)
    private String email;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Post> posts;

    @OneToMany(mappedBy = "fromUser", cascade = CascadeType.ALL)
    private Set<Friendship> sentRequests = new HashSet<>();

    @OneToMany(mappedBy = "toUser", cascade = CascadeType.ALL)
    private Set<Friendship> receivedRequests = new HashSet<>();
}
