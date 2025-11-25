package com.example.PadelCaleruela.dto;


import com.example.PadelCaleruela.model.Visibility;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostDTO {
    private Long id;
    private Long userId;
    private String message;
    private String matchResult;
    private Visibility visibility;
    private LocalDateTime createdAt;
    private String imageUrl;

    // 🔥 Nuevos datos del usuario
    private String username;
    private String userImageUrl;
}
