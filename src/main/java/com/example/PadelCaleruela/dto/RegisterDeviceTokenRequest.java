package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class RegisterDeviceTokenRequest {
    public String token;
    public Long userId;
    public Long tenantId;
    public String platform;
}
