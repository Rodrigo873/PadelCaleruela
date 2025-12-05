package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LockRegisterDTO {
    private Long id;
    private String name;
    private String deviceNumber;
    private String bleName;
    private String deviceMAC;
    private Long pistaId;
}
