package com.example.PadelCaleruela.dto;

import com.example.PadelCaleruela.model.LeagueInvitationType;
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
    private LeagueInvitationType type;
    private String status;
    private LocalDateTime sentAt;
    private String senderProfileImageUrl;
}
