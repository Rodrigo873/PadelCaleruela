package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.AuthResponse;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody User user) {
        AuthResponse response = authService.register(user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody Map<String, String> request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
