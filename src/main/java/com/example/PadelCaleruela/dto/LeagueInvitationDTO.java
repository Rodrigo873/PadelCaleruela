package com.example.PadelCaleruela.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LeagueInvitationDTO {
    private Long id;
    private Long leagueId;
    private String leagueName;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private String status;
    private LocalDateTime sentAt;
}
