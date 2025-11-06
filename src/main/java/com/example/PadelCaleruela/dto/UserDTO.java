package com.example.PadelCaleruela.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private LocalDateTime createdAt;
    private String profileImageUrl;
    private String status;
}
