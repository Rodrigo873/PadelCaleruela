// CheckoutRequest.java
package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.Payment;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CheckoutRequest {
    private Long reservationId;
    private Long userId;
    private Boolean saveCard;

    private BigDecimal amount; // si no lo envías, usamos uno por defecto
    private Payment.Provider provider = Payment.Provider.FAKE; // por ahora FAKE
}
