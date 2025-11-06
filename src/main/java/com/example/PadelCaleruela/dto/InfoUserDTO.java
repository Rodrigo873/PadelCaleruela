package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.Role;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InfoUserDTO {
    private Long id;
    private String fullName;
    private String email;
    private Role role;
    private String profileImageUrl;
}
