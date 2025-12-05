package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class FollowerStatusDTO {
    private Long id;
    private String username;
    private String profileImageUrl;
    private String status; // PENDING / ACCEPTED / REJECTED
}
