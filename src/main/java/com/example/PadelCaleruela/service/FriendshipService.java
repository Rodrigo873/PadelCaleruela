package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.model.Friendship;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.FriendshipRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendshipService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    // Enviar solicitud de amistad
    public void sendFriendRequest(Long fromUserId, Long toUserId) {
        if (fromUserId.equals(toUserId)) {
            throw new IllegalArgumentException("No puedes enviarte una solicitud a ti mismo.");
        }

        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Optional<Friendship> existing = friendshipRepository.findByFromUserAndToUser(fromUser, toUser);
        if (existing.isPresent()) {
            throw new IllegalStateException("Ya existe una solicitud o amistad entre estos usuarios.");
        }

        Friendship friendship = new Friendship();
        friendship.setFromUser(fromUser);
        friendship.setToUser(toUser);
        friendship.setStatus(Friendship.FriendshipStatus.PENDING);

        friendshipRepository.save(friendship);
    }

    // Aceptar solicitud de amistad
    public void acceptFriendRequest(Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }

    // Eliminar amistad
    public void removeFriendship(Long friendshipId) {
        friendshipRepository.deleteById(friendshipId);
    }

    // Obtener lista de amigos de un usuario
    public List<UserDTO> getFriendsOfUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Friendship> friendships = friendshipRepository.findByFromUserOrToUserAndStatus(
                user, user, Friendship.FriendshipStatus.ACCEPTED
        );

        return friendships.stream()
                .map(f -> {
                    User friend = f.getFromUser().equals(user) ? f.getToUser() : f.getFromUser();
                    return new UserDTO(friend.getId(), friend.getUsername(), friend.getEmail());
                })
                .collect(Collectors.toList());
    }
}
