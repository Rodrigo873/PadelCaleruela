package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.NotificationDTO;
import com.example.PadelCaleruela.model.Role;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.service.AuthService;
import com.example.PadelCaleruela.service.NotificationAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationAppService service;
    private final AuthService authService;

    public NotificationController(NotificationAppService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    // -----------------------------------------------------
    //     Notificaciones normales
    // -----------------------------------------------------
    @GetMapping("/user/{userId}")
    public List<NotificationDTO> getUserNotifications(@PathVariable Long userId) {

        User current = authService.getCurrentUser();

        if (!current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver notificaciones de otro usuario.");
        }

        return service.getUserNotifications(userId);
    }

    @GetMapping("/count/{userId}")
    public ResponseEntity<Long> countUnread(@PathVariable Long userId) {
        User current = authService.getCurrentUser();

        // 🔹 1) Usuario normal → solo puede ver SUS notificaciones
        if (authService.isUser()) {
            if (!current.getId().equals(userId)) {
                throw new AccessDeniedException("No puedes ver notificaciones de otro usuario.");
            }
        }

        return ResponseEntity.ok(service.countUnread(userId));
    }

    // -----------------------------------------------------
    //     Notificaciones administrativas
    // -----------------------------------------------------
    @GetMapping("/admin/{userId}")
    public List<NotificationDTO> getAdminNotifications(@PathVariable Long userId) {

        User current = authService.getCurrentUser();

        if (!current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver notificaciones administrativas de otro usuario.");
        }

        if (!(current.getRole() == Role.ADMIN || current.getRole() == Role.SUPERADMIN)) {
            throw new AccessDeniedException("No tienes permisos para ver notificaciones administrativas.");
        }

        return service.getAdminNotifications(userId);
    }

    // -----------------------------------------------------
    //     Marcar UNA como leída
    // -----------------------------------------------------
    @PutMapping("/{id}/read/{userId}")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @PathVariable Long userId) {

        User current = authService.getCurrentUser();

        if (!current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes modificar notificaciones de otro usuario.");
        }

        service.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    // -----------------------------------------------------
    //     Marcar TODAS como leídas
    // -----------------------------------------------------
    @PutMapping("/read-all/{userId}")
    public ResponseEntity<Void> markAllRead(@PathVariable Long userId) {

        User current = authService.getCurrentUser();

        if (!current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes modificar notificaciones de otro usuario.");
        }

        service.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
}
