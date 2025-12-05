package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.FriendshipStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendshipDTO {
    private Long id;

    private Long userId;

    private Long friendId;

    private FriendshipStatus status;

    private LocalDateTime createdAt;

    private String senderUsername;
    private String senderProfileImageUrl;
    private Long senderId;
    private FriendshipStatus statusYou; // 🔥 NUEVO: tu relación hacia él


}
