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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    private BigDecimal amount;

    @Column
    private String currency = "EUR";

    @Column
    private String paymentReference; // client_secret u otro identificador

    @Column
    private String paymentIntentId;

    @Column
    private String paymentMethodId;

    @Column
    private String providerReceiptUrl;

    @Column
    private String cardBrand;

    @Column
    private String cardLast4;

    // 🆕 CRÍTICO PARA MULTI-TENANT STRIPE CONNECT
    @Column(name = "provider_account_id")
    private String providerAccountId;  // ej: acct_1QabcXYZ


    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "ayuntamiento_id")
    private Ayuntamiento ayuntamiento; // si quieres mantener relación directa también


    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
