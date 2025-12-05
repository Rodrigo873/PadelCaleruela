package com.example.PadelCaleruela.dto;

import lombok.Data;

@Data
public class WelockResponse {
    // en DeviceUnLockCommand, data es el comando BLE en HEX
    private String data;
    private int code;
    private String msg;
}
