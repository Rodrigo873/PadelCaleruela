package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.service.WelockAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/welock")
public class WelockDebugController {

    private final WelockAuthService authService;

    public WelockDebugController(WelockAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/token")
    public Map<String, Object> getWelockToken() {
        String token = authService.getToken();
        Map<String, Object> map = new HashMap<>();
        map.put("accessToken", token);
        map.put("expiresAt", authService.getExpiresAt());
        map.put("refreshToken", authService.getRefreshToken());
        return map;
    }
}
