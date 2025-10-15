package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.FriendshipStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendshipDTO {
    private Long id;

    private String userID;

    private String friendId;

    private FriendshipStatus status;

    private LocalDateTime createdAt;

}
