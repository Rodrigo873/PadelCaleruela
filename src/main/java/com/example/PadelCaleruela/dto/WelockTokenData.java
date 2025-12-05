package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class WelockTokenData {
    private String tokenType;
    private String accessToken;
    private long expiresIn;
    private String refreshToken;
    private long refreshTokenExpires;
}
