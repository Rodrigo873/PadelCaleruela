package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.FriendshipDTO;
import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.model.Friendship;
import com.example.PadelCaleruela.model.FriendshipStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.FriendshipRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

    // 🔹 Enviar solicitud de amistad
    public void sendFriendRequest(Long fromUserId, Long toUserId) {
        if (fromUserId.equals(toUserId)) return; // Evitar auto-seguirse

        User from = userRepository.findById(fromUserId).orElseThrow();
        User to = userRepository.findById(toUserId).orElseThrow();

        Optional<Friendship> existingOpt = friendshipRepository.findByUserIdAndFriendId(fromUserId, toUserId);

        if (existingOpt.isPresent()) {
            Friendship existing = existingOpt.get();

            // ✅ Si fue rechazado previamente, se puede reenviar
            if (existing.getStatus() == FriendshipStatus.REJECTED) {
                existing.setStatus(FriendshipStatus.PENDING);
                friendshipRepository.save(existing);
                return;
            }

            // 🚫 Si ya está pendiente o aceptado, no reenviar
            if (existing.getStatus() == FriendshipStatus.PENDING || existing.getStatus() == FriendshipStatus.ACCEPTED) {
                return;
            }

        } else {
            // 🆕 Crear nueva solicitud si no existía
            Friendship f = new Friendship();
            f.setUser(from);
            f.setFriend(to);
            f.setStatus(FriendshipStatus.PENDING);
            friendshipRepository.save(f);
        }
    }

    /**
     * 🔹 Seguidores (usuarios que siguen al userId)
     */
    public List<UserDTO> getFollowers(Long userId) {
        List<User> followers = friendshipRepository.findFollowersByUserId(userId);
        return followers.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 🔹 Usuarios seguidos (a quienes sigue el userId)
     */
    public List<UserDTO> getFollowing(Long userId) {
        List<User> following = friendshipRepository.findFollowingByUserId(userId);
        return following.stream().map(this::toDTO).collect(Collectors.toList());
    }


    // 🔹 Aceptar solicitud de amistad
    @Transactional
    public void acceptFriendRequest(Long userId, Long friendId) {
        // 🧠 IMPORTANTE: buscamos la solicitud donde el otro (friendId) fue quien la envió
        Friendship f = friendshipRepository
                .findByUserIdAndFriendId(friendId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe una solicitud (PENDING) de user_id=" + friendId + " a friend_id=" + userId));

        if (f.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("La solicitud no está en estado PENDING.");
        }

        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);
    }





    //rechazar
    public void rejectFriendRequest(Long userId, Long senderId) {
        // Buscar la solicitud que envió el "sender" al "user"
        Friendship friendship = friendshipRepository.findByUserAndFriend(
                userRepository.findById(senderId)
                        .orElseThrow(() -> new RuntimeException("Usuario que envió la solicitud no encontrado.")),
                userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Usuario que recibe la solicitud no encontrado."))
        ).orElseThrow(() -> new RuntimeException("No existe una solicitud pendiente de este usuario."));

        // Verificar que esté pendiente
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("Esta solicitud ya fue aceptada.");
        }
        if (friendship.getStatus() == FriendshipStatus.REJECTED) {
            throw new IllegalStateException("Esta solicitud ya fue rechazada.");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }





    // 🔹 Eliminar amistad
    public void removeFriendship(Long userId, Long friendId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new RuntimeException("Amigo no encontrado."));

        // Buscar la relación de amistad en ambos sentidos
        Optional<Friendship> friendshipOpt = friendshipRepository.findByUserAndFriend(user, friend);

        if (friendshipOpt.isEmpty()) {
            friendshipOpt = friendshipRepository.findByUserAndFriend(friend, user);
        }

        Friendship friendship = friendshipOpt
                .orElseThrow(() -> new RuntimeException("No existe una amistad entre estos usuarios."));

        // Solo se puede eliminar una amistad aceptada
        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("Solo puedes eliminar amistades aceptadas.");
        }

        friendshipRepository.delete(friendship);
    }


    // 🔹 Obtener lista de amigos de un usuario
    public List<UserDTO> getFriendsOfUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        List<Friendship> friendships = friendshipRepository.findAcceptedFriendshipsByUserId(userId);

        return friendships.stream()
                .map(f -> {
                    User friend = f.getUser().equals(user) ? f.getFriend() : f.getUser();
                    UserDTO dto = new UserDTO();
                    dto.setId(friend.getId());
                    dto.setUsername(friend.getUsername());
                    dto.setFullName(friend.getFullName());
                    dto.setEmail(friend.getEmail());
                    dto.setCreatedAt(friend.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
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

                    // 🧩 Datos del usuario que envió la solicitud
                    dto.setSenderUsername(f.getUser().getUsername());
                    dto.setSenderProfileImageUrl(f.getUser().getProfileImageUrl());

                    return dto;
                })
                .collect(Collectors.toList());
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
