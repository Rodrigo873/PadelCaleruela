package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.NotificationDTO;
import com.example.PadelCaleruela.model.Notification;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.NotificationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationAppService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;

    public NotificationAppService(NotificationRepository repo, UserRepository userRepo) {
        this.repository = repo;
        this.userRepository = userRepo;
    }

    // Guardar notificación
    public void saveNotification(Notification notification) {
        repository.save(notification);
    }

    // Obtener notificaciones normales
    public List<NotificationDTO> getUserNotifications(Long userId) {
        return fetchNotifications(userId, false)  // trae SOLO no-admin
                .stream()
                .filter(n -> !n.isReadFlag())      // solo no leídas
                .collect(Collectors.toList());
    }

    public long countUnread(Long userId) {
        return repository.countUnreadByUserId(userId);
    }


    // Obtener notificaciones administrativas
    public List<NotificationDTO> getAdminNotifications(Long userId) {
        return fetchNotifications(userId, true);
    }

    private List<NotificationDTO> fetchNotifications(Long userId, boolean adminOnly) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(n -> adminOnly
                        ? n.getType().name().startsWith("ADMIN_")
                        : !n.getType().name().startsWith("ADMIN_"))
                .map(n -> {
                    User sender = (n.getSenderId() != null)
                            ? userRepository.findById(n.getSenderId()).orElse(null)
                            : null;
                    return toDTO(n, sender);
                })
                .collect(Collectors.toList());
    }

    // Marcar una como leída
    public void markAsRead(Long id, Long userId) {

        Notification notif = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada."));

        if (!notif.getUserId().equals(userId)) {
            throw new RuntimeException("No puedes modificar notificaciones de otro usuario.");
        }

        notif.setReadFlag(true);
        repository.save(notif);
    }

    // Marcar todas como leídas
    public void markAllAsRead(Long userId) {
        List<Notification> notifs =
                repository.findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(userId);

        notifs.forEach(n -> n.setReadFlag(true));
        repository.saveAll(notifs);
    }

    // Conversión a DTO
    public NotificationDTO toDTO(Notification n, User sender) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setTitle(n.getTitle());
        dto.setMessage(n.getMessage());
        dto.setType(n.getType());
        dto.setReadFlag(n.isReadFlag());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setExtraData(n.getExtraData());

        if (sender != null) {
            dto.setSenderId(sender.getId());
            dto.setSenderName(sender.getUsername());
            dto.setSenderImageUrl(sender.getProfileImageUrl());
        }

        return dto;
    }
}
