package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.DeviceToken;
import com.example.PadelCaleruela.model.NotificationType;
import com.example.PadelCaleruela.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNotificationService {

    private final DeviceTokenRepository tokenRepository;
    private final NotificationService notificationService;  // envía a Firebase
    private final NotificationFactory factory;              // genera texto automático

    public void sendToUser(Long userId, String title, String body) {

        List<DeviceToken> tokens = tokenRepository.findByUserId(userId);

        if (tokens.isEmpty()) {
            System.out.println("⚠ Usuario sin tokens registrados → " + userId);
            return;
        }

        for (DeviceToken token : tokens) {
            try {
                notificationService.sendPush(token.getToken(), title, body);
            } catch (Exception ex) {
                System.out.println("⚠ Error enviando push al token " + token.getToken());
            }
        }
    }


    /**
     * 👉 Versión automática usando NotificationFactory
     */
    public void sendToUser(Long userId, String senderName, NotificationType type) {

        String title = factory.getTitle(type);
        String body = factory.getMessage(type, senderName);

        sendToUser(userId, title, body);
    }
}
