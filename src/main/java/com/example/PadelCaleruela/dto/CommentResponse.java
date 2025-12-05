package com.example.PadelCaleruela.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommentResponse {
    private Long id;
    private String text;
    private LocalDateTime createdAt;

    private Long userId;
    private String username;
    private String profileImageUrl;
}
