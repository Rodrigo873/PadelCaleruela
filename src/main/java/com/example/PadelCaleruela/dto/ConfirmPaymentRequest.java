// ConfirmPaymentRequest.java
package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class ConfirmPaymentRequest {
    private Long paymentId;
    private boolean success;      // true = pagado ok; false = fallo simulado
    private String referenceHint; // simulamos “id” devuelto por pasarela
}
