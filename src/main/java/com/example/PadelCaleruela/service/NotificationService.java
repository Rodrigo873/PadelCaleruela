package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.repository.DeviceTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final DeviceTokenRepository tokenRepository;

    public void sendPush(String token, String title, String body) {

        if (token == null || token.isBlank()) {
            return;
        }

        Message message = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setToken(token)
                .build();

        try {

            FirebaseMessaging.getInstance().send(message);

        } catch (FirebaseMessagingException fme) {

            if ("UNREGISTERED".equals(fme.getErrorCode())) {
                System.out.println("⚠ Token inválido → eliminado: " + token);
                tokenRepository.deleteByToken(token);
                return;
            }

            throw new RuntimeException("Error enviando push: " + fme.getErrorCode(), fme);

        } catch (Exception ex) {
            throw new RuntimeException("Error inesperado enviando push: " + ex.getMessage(), ex);
        }
    }

}
