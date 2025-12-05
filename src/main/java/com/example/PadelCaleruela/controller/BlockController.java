package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.service.AuthService;
import com.example.PadelCaleruela.service.BlockService;
import com.example.PadelCaleruela.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final BlockService blockService;

    // =====================================================
    // 1. 🏛️ Ayuntamiento bloquea a un usuario
    // =====================================================
    @PostMapping("/ayuntamiento/{userId}")
    public ResponseEntity<?> bloquearDesdeAyuntamiento(@PathVariable Long userId) {

        User current = authService.getCurrentUser();

        if (!authService.isAdmin()) {
            return ResponseEntity.status(403).body("Solo un admin del ayuntamiento puede bloquear.");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        blockService.bloquearUsuarioDesdeAyuntamiento(current.getAyuntamiento(), target);

        return ResponseEntity.ok().build();
    }

    // =====================================================
    // 2. 👤 Usuario bloquea a otro usuario
    // =====================================================
    @PostMapping("/user/{userId}")
    public ResponseEntity<?> bloquearUsuario(@PathVariable Long userId) {

        User current = authService.getCurrentUser();

        if (!current.getId().equals(authService.getCurrentUser().getId())) {
            return ResponseEntity.status(403).body("No puedes bloquear desde otra cuenta.");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (current.getId().equals(target.getId())) {
            return ResponseEntity.badRequest().body("No puedes bloquearte a ti mismo.");
        }

        blockService.bloquearUsuarioDesdeUsuario(current, target);

        return ResponseEntity.ok().build();
    }

    // =====================================================
    // DESBLOQUEAR — USUARIO
    // =====================================================
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<?> desbloquearUsuario(@PathVariable Long userId) {

        User current = authService.getCurrentUser();

        blockService.desbloquearUsuarioDesdeUsuario(current.getId(), userId);

        return ResponseEntity.ok().build();
    }

    // =====================================================
    // DESBLOQUEAR — AYUNTAMIENTO
    // =====================================================
    @DeleteMapping("/ayuntamiento/{userId}")
    public ResponseEntity<?> desbloquearDesdeAyuntamiento(@PathVariable Long userId) {

        User current = authService.getCurrentUser();

        if (!authService.isAdmin()) {
            return ResponseEntity.status(403).body("Solo un admin del ayuntamiento puede desbloquear.");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        blockService.desbloquearUsuarioDesdeAyuntamiento(
                current.getAyuntamiento().getId(),
                target.getId()
        );

        return ResponseEntity.ok().build();
    }
}
