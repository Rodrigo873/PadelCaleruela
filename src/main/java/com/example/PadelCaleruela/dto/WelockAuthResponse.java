package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class WelockAuthResponse {
    private WelockTokenData data;
    private int code;
    private String msg;
}
