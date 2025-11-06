package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.service.PasswordService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) throws MessagingException {
        passwordService.sendResetCode(body.get("email"));
        return ResponseEntity.ok("Se ha enviado un código de recuperación a tu correo.");
    }

    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> body) {
        boolean valid = passwordService.verifyCode(body.get("code"));
        return valid
                ? ResponseEntity.ok("Código válido.")
                : ResponseEntity.badRequest().body("Código incorrecto o expirado.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        passwordService.resetPassword(body.get("code"), body.get("newPassword"));
        return ResponseEntity.ok("Contraseña actualizada correctamente.");
    }
}
