// DTO: ReservationPlayerDTO.java
package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReservationPlayerDTO {
    private String username;
    private String fullName;
    private String status; // ACEPTADA, PENDIENTE, CANCELADA
}
