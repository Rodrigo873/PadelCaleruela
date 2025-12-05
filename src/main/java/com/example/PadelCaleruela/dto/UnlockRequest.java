package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class UnlockRequest {
    private Long reservationId;
    private Long userId;
    private String deviceNumber;
    private String bleName;
    private String power;
    private String randomFactor;
}