package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.FriendshipDTO;
import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.FriendshipRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final UserNotificationService userNotificationService;
    private final NotificationFactory notificationFactory;
    private final NotificationAppService notificationAppService;


    // 🔹 Enviar solicitud de amistad
    public void sendFriendRequest(Long fromUserId, Long toUserId) {

        User current = authService.getCurrentUser();

        User from = userRepository.findById(fromUserId)
                .orElseThrow(() -> new RuntimeException("Usuario origen no encontrado"));

        User to = userRepository.findById(toUserId)
                .orElseThrow(() -> new RuntimeException("Usuario destino no encontrado"));

        // USER → solo puede enviar su propia solicitud
        if (authService.isUser() && !current.getId().equals(fromUserId)) {
            throw new RuntimeException("No puedes enviar solicitudes en nombre de otro usuario.");
        }

        // ADMIN → solo si ambos usuarios pertenecen a su ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(from.getAyuntamiento());
            authService.ensureSameAyuntamiento(to.getAyuntamiento());
        }

        // SUPERADMIN → sin restricciones

        if (fromUserId.equals(toUserId)) return;

        Optional<Friendship> existingOpt =
                friendshipRepository.findByUserIdAndFriendId(fromUserId, toUserId);

        if (existingOpt.isPresent()) {
            Friendship existing = existingOpt.get();

            if (existing.getStatus() == FriendshipStatus.REJECTED) {
                existing.setStatus(FriendshipStatus.PENDING);
                friendshipRepository.save(existing);
                return;
            }

            if (existing.getStatus() == FriendshipStatus.PENDING
                    || existing.getStatus() == FriendshipStatus.ACCEPTED) {
                return; // ya existe
            }
        }

        Friendship f = new Friendship();
        f.setUser(from);
        f.setFriend(to);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(f);

        try {
            userNotificationService.sendToUser(
                    to.getId(),
                    from.getUsername(),
                    NotificationType.FRIEND_REQUEST
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 🔹 Seguidores (usuarios que siguen al userId)
     */
    public int getFollowersCount(Long userId) {

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // ADMIN → debe respetar ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target.getAyuntamiento());
        }

        return friendshipRepository.countFollowers(userId);
    }




    /**
     * 🔹 Usuarios seguidos (a quienes sigue el userId)
     */
    public int getFollowingCount(Long userId) {

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // ADMIN → debe respetar ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target.getAyuntamiento());
        }

        return friendshipRepository.countFollowing(userId);
    }




    // 🔹 Aceptar solicitud de amistad
    @Transactional
    public void acceptFriendRequest(Long userId, Long friendId) {

        User current = authService.getCurrentUser();

        User receiver = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario receptor no encontrado"));

        User sender = userRepository.findById(friendId)
                .orElseThrow(() -> new RuntimeException("Usuario origen no encontrado"));

        // USER solo acepta sus propias solicitudes
        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new RuntimeException("No puedes aceptar solicitudes dirigidas a otro usuario.");
        }

        // ADMIN solo dentro de su ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(receiver.getAyuntamiento());
            authService.ensureSameAyuntamiento(sender.getAyuntamiento());
        }

        Friendship f = friendshipRepository
                .findByUserIdAndFriendId(friendId, userId)
                .orElseThrow(() -> new IllegalArgumentException("No existe una solicitud pendiente."));

        if (f.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("La solicitud no está pendiente.");
        }

        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        // ---------------------------------------------------------
        //  🔥 Crear la NOTIFICACIÓN en base de datos (solo BD)
        // ---------------------------------------------------------
        NotificationType type = NotificationType.FRIEND_ACCEPT;
        String title = notificationFactory.getTitle(type);
        String message = notificationFactory.getMessage(type, receiver.getUsername());

        Notification n = new Notification();
        n.setUserId(sender.getId());      // el que recibe la notificación
        n.setSenderId(receiver.getId());  // quien la acepta
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setExtraData(null); // si quieres añadir datos luego

        notificationAppService.saveNotification(n);


        try {
            userNotificationService.sendToUser(
                    sender.getId(),
                    receiver.getUsername(),
                    NotificationType.FRIEND_REQUEST
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    //rechazar
    public void rejectFriendRequest(Long userId, Long senderId) {

        User current = authService.getCurrentUser();

        User receiver = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario receptor no encontrado"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Usuario origen no encontrado"));

        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new RuntimeException("No puedes rechazar solicitudes de otro.");
        }

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(receiver.getAyuntamiento());
            authService.ensureSameAyuntamiento(sender.getAyuntamiento());
        }

        Friendship friendship = friendshipRepository.findByUserAndFriend(sender, receiver)
                .orElseThrow(() -> new RuntimeException("No existe una solicitud pendiente."));

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }



    public void follow(Long followerId, Long followedId) {

        if (followerId.equals(followedId)) return;

        Optional<Friendship> existing =
                friendshipRepository.findByUserIdAndFriendId(followerId, followedId);

        if (existing.isPresent()) {
            Friendship fr = existing.get();
            if (fr.getStatus() != FriendshipStatus.ACCEPTED) {
                fr.setStatus(FriendshipStatus.ACCEPTED);
                friendshipRepository.save(fr);
            }
            return;
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User followed = userRepository.findById(followedId)
                .orElseThrow(() -> new RuntimeException("Usuario seguido no encontrado"));

        Friendship f = new Friendship();
        f.setUser(follower);       // el que sigue
        f.setFriend(followed);     // seguido
        f.setStatus(FriendshipStatus.ACCEPTED);

        friendshipRepository.save(f);
    }



    // 🔹 Eliminar amistad
    public void removeFriendship(Long userId, Long friendId) {

        User current = authService.getCurrentUser();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new RuntimeException("Amigo no encontrado"));

        // USER → solo eliminar sus amistades
        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new RuntimeException("No puedes eliminar amistades de otros.");
        }

        // ADMIN → solo si ambos son del mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(user.getAyuntamiento());
            authService.ensureSameAyuntamiento(friend.getAyuntamiento());
        }

        // SUPERADMIN → sin restricciones

        Optional<Friendship> friendshipOpt =
                friendshipRepository.findByUserAndFriend(user, friend);

        if (friendshipOpt.isEmpty()) {
            friendshipOpt =
                    friendshipRepository.findByUserAndFriend(friend, user);
        }

        Friendship friendship = friendshipOpt
                .orElseThrow(() -> new RuntimeException("No existe una amistad entre estos usuarios."));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new RuntimeException("Solo puedes eliminar amistades aceptadas.");
        }

        friendshipRepository.delete(friendship);
    }



    // 🔹 Obtener lista de amigos de un usuario
    public List<UserDTO> getFriendsOfUser(Long userId) {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new RuntimeException("No puedes ver los amigos de otro usuario.");
        }

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target.getAyuntamiento());
        }

        return friendshipRepository.findAcceptedFriendshipsByUserId(userId)
                .stream()
                .map(f -> {
                    User friend = f.getUser().equals(target) ? f.getFriend() : f.getUser();
                    return toDTO(friend);
                })
                .toList();
    }



    //Lista de solicitudes recibidas
    public List<FriendshipDTO> getPendingRequests(Long userId) {

        return friendshipRepository.findAll().stream()
                .filter(f -> f.getFriend().getId().equals(userId)
                        && f.getStatus() == FriendshipStatus.PENDING)
                .map(f -> {

                    FriendshipDTO dto = new FriendshipDTO();
                    dto.setId(f.getId());
                    dto.setUserId(f.getUser().getId());
                    dto.setFriendId(f.getFriend().getId());
                    dto.setStatus(f.getStatus());
                    dto.setCreatedAt(f.getCreatedAt());

                    // Datos del usuario que envió la solicitud
                    dto.setSenderId(f.getUser().getId());
                    dto.setSenderUsername(f.getUser().getUsername());
                    dto.setSenderProfileImageUrl(f.getUser().getProfileImageUrl());

                    // ==========================================================
                    // 🔥 NUEVA LÓGICA → tu relación hacia el sender
                    // ==========================================================
                    Friendship reverse = friendshipRepository
                            .findByUserIdAndFriendId(userId, f.getUser().getId())
                            .orElse(null);

                    if (reverse == null) {
                        dto.setStatusYou(FriendshipStatus.REJECTED); // sin relación
                    } else {
                        dto.setStatusYou(reverse.getStatus()); // PENDING o ACCEPTED
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }



    public long getPendingCount(Long userId) {
        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver solicitudes de otro usuario.");
        }

        return friendshipRepository.countPendingByUserId(userId);
    }

    public boolean hasPending(Long userId) {
        return getPendingCount(userId) > 0;
    }


    private FriendshipDTO mapToDTO(Friendship friendship) {
        FriendshipDTO dto = new FriendshipDTO();
        dto.setId(friendship.getId());
        dto.setUserId(friendship.getUser().getId());
        dto.setFriendId(friendship.getFriend().getId());
        dto.setStatus(friendship.getStatus());
        dto.setCreatedAt(friendship.getCreatedAt());
        return dto;
    }

    public FriendshipDTO getFriendshipStatus(Long userId, Long otherUserId) {
        Optional<Friendship> direct = friendshipRepository.findByUserIdAndFriendId(userId, otherUserId);

        FriendshipDTO dto = new FriendshipDTO();
        dto.setUserId(userId);
        dto.setFriendId(otherUserId);

        if (direct.isPresent()) {
            Friendship friendship = direct.get();
            dto.setStatus(friendship.getStatus());
            dto.setSenderId(friendship.getUser().getId());
        } else {
            dto.setStatus(null); // No hay relación
        }

        return dto;
    }




    /** 🔹 Eliminar una amistad existente (o relación de seguimiento) */
    public String deleteFriendship(Long userId, Long friendId) {
        // Buscamos ambas posibles direcciones de la relación
        Optional<Friendship> friendship = friendshipRepository.findByUserIdAndFriendId(userId, friendId);
        if (friendship.isEmpty()) {
            friendship = friendshipRepository.findByUserIdAndFriendId(friendId, userId);
        }

        if (friendship.isPresent()) {
            friendshipRepository.delete(friendship.get());
            return "Amistad eliminada correctamente.";
        } else {
            return "No existe una relación de amistad entre estos usuarios.";
        }
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setProfileImageUrl(user.getProfileImageUrl());
        return dto;
    }
}
