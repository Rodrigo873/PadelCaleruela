// PaymentDTO.java
package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.Payment;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentDTO {
    private Long id;
    private Long reservationId;
    private Long userId;
    private Payment.Provider provider;
    private Payment.Status status;
    private BigDecimal amount;
    private String currency;
    private String paymentReference;
    private LocalDateTime createdAt;
}
