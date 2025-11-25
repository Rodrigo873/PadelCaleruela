package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.RegisterDeviceTokenRequest;
import com.example.PadelCaleruela.service.DeviceTokenService;
import com.example.PadelCaleruela.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    private final NotificationService notificationService;

    public DeviceTokenController(DeviceTokenService deviceTokenService,NotificationService notificationService) {
        this.deviceTokenService = deviceTokenService;
        this.notificationService=notificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterDeviceTokenRequest request) {
        deviceTokenService.registerToken(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send-test")
    public void sendTest(@RequestParam String token) throws Exception {
        notificationService.sendPush(token, "Prueba", "Funciona tu notificación");
    }

}
