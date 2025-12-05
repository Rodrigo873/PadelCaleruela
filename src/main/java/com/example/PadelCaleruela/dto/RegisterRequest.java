package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String deviceNumber;
    private String deviceName;
    private String userId;
}