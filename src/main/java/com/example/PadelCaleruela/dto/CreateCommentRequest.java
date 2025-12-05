package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class CreateCommentRequest {
    private Long userId;
    private String text;
}
