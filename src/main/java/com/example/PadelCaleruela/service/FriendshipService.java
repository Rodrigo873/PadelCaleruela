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
    public void sendFriendRequest(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            throw new IllegalArgumentException("No puedes enviarte una solicitud a ti mismo.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new RuntimeException("Amigo no encontrado."));

        Optional<Friendship> existing = friendshipRepository.findByUserAndFriend(user, friend);
        if (existing.isPresent()) {
            throw new IllegalStateException("Ya existe una solicitud o amistad entre estos usuarios.");
        }

        Friendship friendship = new Friendship();
        friendship.setUser(user);
        friendship.setFriend(friend);
        friendship.setStatus(FriendshipStatus.PENDING);

        friendshipRepository.save(friendship);
    }

    // 🔹 Aceptar solicitud de amistad
    public void acceptFriendRequest(Long userId, Long senderId) {
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

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
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
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
