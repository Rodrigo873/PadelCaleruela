package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LikeRepository;
import com.example.PadelCaleruela.repository.PostRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class LikeService {

    private final LikeRepository likeRepository;
    private final AuthService authService;
    private final PostRepository postRepository;
    private final NotificationAppService notificationAppService;
    private final NotificationFactory notificationFactory;
    private final UserNotificationService userNotificationService;

    // 👍 Dar like
    @Transactional
    public void likePost(Long postId) {

        User liker = authService.getCurrentUser(); // quien da like

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado"));

        User owner = post.getUser(); // dueño del post

        // ❌ Si ya dio like → evitar duplicado
        if (likeRepository.existsByUserAndPost(liker, post)) {
            return;
        }

        // ❤️ Guardamos el like
        Like like = new Like();
        like.setUser(liker);
        like.setPost(post);
        likeRepository.save(like);

        // ============================
        // 🔔 Notificación interna (BD)
        // ============================
        NotificationType type = NotificationType.POST_LIKED;

        String title = notificationFactory.getTitle(type);
        String message = notificationFactory.getMessage(type, liker.getUsername());

        Notification n = new Notification();
        n.setUserId(owner.getId());      // dueño del post
        n.setSenderId(liker.getId());    // quien da like
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setExtraData(post.getId().toString()); // ID del post para abrirlo

        notificationAppService.saveNotification(n);

        // ============================
        // 📲 PUSH NOTIFICATION
        // ============================
        try {
            userNotificationService.sendToUser(
                    owner.getId(),          // receptor
                    liker.getUsername(),    // nombre del que da like
                    NotificationType.POST_LIKED
            );
        } catch (Exception e) {
            throw new RuntimeException("Error enviando push de like", e);
        }
    }



    // 👎 Quitar like
    public void unlikePost(Long postId) {
        User user = authService.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado"));

        likeRepository.deleteByUserAndPost(user, post);
    }

    // 🔢 Obtener número de likes
    public long getLikes(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado"));

        return likeRepository.countByPost(post);
    }
}

