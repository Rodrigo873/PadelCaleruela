package com.example.PadelCaleruela.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // ✅ evita incluir relaciones
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include // ✅ solo el ID define igualdad
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.OFFLINE;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    // Relaciones
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<Post> posts;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @ToString.Exclude
    private Set<Friendship> sentRequests = new HashSet<>();

    @OneToMany(mappedBy = "friend", cascade = CascadeType.ALL)
    @ToString.Exclude
    private Set<Friendship> receivedRequests = new HashSet<>();

    @ManyToMany(mappedBy = "jugadores")
    @ToString.Exclude
    private Set<Reservation> reservas = new HashSet<>();

    // User.java
    @Column(unique = true)
    private String stripeCustomerId;

    @Column
    private String defaultPaymentMethodId; // opcional: favorito del usuario

    @ManyToOne
    @JoinColumn(name = "ayuntamiento_id")
    private Ayuntamiento ayuntamiento;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Comment> comments;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }


}
