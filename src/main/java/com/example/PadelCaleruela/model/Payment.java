// Payment.java
package com.example.PadelCaleruela.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "payments")
public class Payment {

    public enum Provider { FAKE, STRIPE, PAYPAL }
    public enum Status { PENDING, SUCCEEDED, FAILED, CANCELED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // quién pagó

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider = Provider.FAKE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; // precio total

    @Column
    private String currency = "EUR";

    @Column
    private String paymentReference; // id de pasarela (client_secret, orderId, etc.)

    // Payment.java
    @Column
    private String paymentIntentId;  // id real de Stripe

    @Column
    private String paymentMethodId;  // último PM usado (snapshot)

    @Column
    private String providerReceiptUrl; // URL recibo Stripe (útil para soporte)

    @Column
    private String cardBrand;  // snapshot para mostrar al user
    @Column
    private String cardLast4;


    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
