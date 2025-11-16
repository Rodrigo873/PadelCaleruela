package com.example.PadelCaleruela.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserRegister {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private LocalDateTime createdAt;
    private String codigoPostal;
    private String password;
}
