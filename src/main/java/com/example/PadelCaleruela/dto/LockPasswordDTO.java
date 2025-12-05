package com.example.PadelCaleruela.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LockPasswordDTO {

    private Long lockId;
    private String bleName;

    private String password;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
