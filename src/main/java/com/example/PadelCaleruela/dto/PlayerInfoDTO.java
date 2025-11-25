package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.UserStatus;
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
    private UserStatus status;
}