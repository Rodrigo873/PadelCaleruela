package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.NotificationType;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class NotificationDTO {

    private Long id;
    private String title;
    private String message;
    private NotificationType type;

    private boolean readFlag;
    private LocalDateTime createdAt;

    private String extraData;

    private Long senderId;
    private String senderName;      // opcional
    private String senderImageUrl;  // opcional

    // Getters y setters...
}
