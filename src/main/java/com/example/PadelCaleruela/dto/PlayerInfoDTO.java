package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInfoDTO {
    private Long id;
    private String username;
    private String profileImageUrl;
    private boolean accepted;
}