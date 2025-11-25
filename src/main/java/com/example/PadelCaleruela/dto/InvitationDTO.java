package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.InvitationStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InvitationDTO {
    private Long id;
    private String senderUsername;
    private String receiverUsername;
    private LocalDateTime createdAt;
    private InvitationStatus status;

    // Datos básicos de la reserva
    private Long reservationId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String senderProfileImageUrl;
}
