package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.dto.InfoUserDTO;
import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.model.Role;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.UserStatus;
import com.example.PadelCaleruela.repository.FriendshipRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private final FriendshipRepository friendshipRepository;

    private final ReservationRepository reservationRepository;
    private final EmailService emailService;

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/profile-images/";


    public UserService(UserRepository repo,BCryptPasswordEncoder passwordEncoder,FriendshipRepository friendshipRepository,
                       ReservationRepository reservationRepository,EmailService emailService) {
        this.userRepository = repo;
        this.passwordEncoder=passwordEncoder;
        this.friendshipRepository=friendshipRepository;
        this.reservationRepository=reservationRepository;
        this.emailService=emailService;
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<InfoUserDTO> getAllInfoUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDTOinfo)
                .collect(Collectors.toList());
    }

    @Transactional
    public User updateUserRole(Long userId, String newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + userId));

        // Validar que el rol sea correcto
        if (!newRole.equalsIgnoreCase("USER") && !newRole.equalsIgnoreCase("ADMIN")) {
            throw new IllegalArgumentException("Rol no válido. Debe ser USER o ADMIN.");
        }

        user.setRole(Role.valueOf(newRole.toUpperCase()));
        return userRepository.save(user);
    }

    public UserDTO saveUser(User user) {
        // 🔹 Comprobación de username único
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
        }

        // 🔹 Comprobación opcional de email único
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }
        String pass="";
        if (user.getPassword().isEmpty()){
            // 🔤 Conjunto de caracteres válidos
            final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

            // 🔒 Generador seguro de números aleatorios
            SecureRandom random = new SecureRandom();
            StringBuilder sb = new StringBuilder(8);

            // 📏 Generar la contraseña
            for (int i = 0; i < 8; i++) {
                int index = random.nextInt(CHARS.length());
                sb.append(CHARS.charAt(index));
            }

            String generatedPassword = sb.toString();
            pass=generatedPassword;
            // 🔐 Cifrar antes de guardar
            user.setPassword(passwordEncoder.encode(generatedPassword));
        }else {
            // 🔹 Encriptar la contraseña antes de guardar
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        // 🔹 Guardar usuario
        User saved = userRepository.save(user);

        try {
            emailService.sendHtmlEmail(
                    user.getEmail(),
                    "Usuario creado correctamente",
                    "<h3>¡Hola " + user.getUsername() + "!</h3>" +
                            "<p>Bienvenido a la mejor aplicación de pádel del mundo 🎾.</p>"+
                            "<p>Se te ha asignado una contraseña al azar, puedes cambiarla desde la app.</p>"+
                            "<p>La contraseña es="+pass+"</p>"


            );
        } catch (MessagingException e) {
            // ⚠️ Evita que la app crashee si el correo falla
            System.err.println("Error al enviar el correo: " + e.getMessage());
        }

        // 🔹 Retornar el DTO
        return toDTO(saved);
    }


    public UserDTO getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    // 🔍 Buscar usuarios por username (insensible a mayúsculas/minúsculas)
    public List<UserDTO> searchUsersByUsername(String username) {
        List<User> users = userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(username, username);
        return users.stream()
                .map(user -> {
                    UserDTO dto = new UserDTO();
                    dto.setId(user.getId());
                    dto.setUsername(user.getUsername());
                    dto.setFullName(user.getFullName());
                    dto.setEmail(user.getEmail());
                    dto.setCreatedAt(user.getCreatedAt());
                    dto.setStatus(user.getStatus() != null ? user.getStatus().toString() : null);
                    dto.setProfileImageUrl(user.getProfileImageUrl());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public UserDTO updateUserProfile(Long id,String fullName, String username, String email, String password, MultipartFile profileImage) throws IOException {
        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("Usuario no encontrado");
        }

        User user = optionalUser.get();

        // Actualizar campos si se han enviado
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }
        if (username != null && !username.isBlank()) {
            user.setUsername(username);
        }
        if (email != null && !email.isBlank()) {
            user.setEmail(email);
        }
        if (password != null && !password.isBlank()) {
            user.setPassword(passwordEncoder.encode(password));
        }

        // Guardar imagen si se ha enviado
        if (profileImage != null && !profileImage.isEmpty()) {
            String imageUrl = saveProfileImage(profileImage);
            user.setProfileImageUrl(imageUrl);
        }
        User user1=userRepository.save(user);
        UserDTO userDTO=toDTO(user1);
        return userDTO;
    }

    // ✅ Actualizar el estado del usuario
    @Transactional
    public void updateUserStatus(Long userId, String newStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setStatus(UserStatus.valueOf(newStatus.toUpperCase()));
        userRepository.save(user);
    }


    private String saveProfileImage(MultipartFile file) throws IOException {
        // Crear carpeta si no existe
        Path uploadPath = Paths.get("uploads/profile-images");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Sanear nombre del archivo (evita espacios y caracteres raros)
        String originalFileName = file.getOriginalFilename();
        String sanitizedFileName = originalFileName != null
                ? originalFileName.replaceAll("\\s+", "_")
                : "unknown";

        // Generar nombre único
        String filename = UUID.randomUUID() + "_" + sanitizedFileName;
        Path filePath = uploadPath.resolve(filename);

        // Guardar archivo
        Files.write(filePath, file.getBytes());

        // Retornar la ruta pública (que el frontend pueda acceder)
        return "/uploads/profile-images/" + filename;
    }


    // 🔹 Actualizar usuario
    public Optional<UserDTO> updateUser(Long id, User updatedUser) {
        return userRepository.findById(id).map(user -> {

            // 🔹 Verificar si el nuevo username pertenece a otro usuario
            if (!user.getUsername().equals(updatedUser.getUsername()) &&
                    userRepository.findByUsername(updatedUser.getUsername()).isPresent()) {
                throw new IllegalArgumentException("El nombre de usuario ya está en uso por otro usuario.");
            }

            // 🔹 Verificar si el nuevo email pertenece a otro usuario
            if (!user.getEmail().equals(updatedUser.getEmail()) &&
                    userRepository.findByEmail(updatedUser.getEmail()).isPresent()) {
                throw new IllegalArgumentException("El correo electrónico ya está registrado por otro usuario.");
            }

            // 🔹 Actualizar los campos permitidos
            user.setUsername(updatedUser.getUsername());
            user.setEmail(updatedUser.getEmail());
            user.setFullName(updatedUser.getFullName());

            // 🔹 Solo encriptar si se envía una nueva contraseña
            if (updatedUser.getPassword() != null && !updatedUser.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
            }

            // 🔹 Guardar cambios y devolver DTO
            User savedUser = userRepository.save(user);
            return toDTO(savedUser);
        });
    }

    /**
     * 🔹 Obtiene los amigos de mis amigos (sugerencias)
     */
    public List<UserDTO> getSuggestedPlayers(Long userId) {
        // ✅ 1. Usuarios que yo sigo (amistades aceptadas o pendientes)
        List<Long> followingIds = friendshipRepository.findFriendIdsByUserId(userId);

        // ✅ 2. Usuarios que me siguen (amistades aceptadas o pendientes)
        List<Long> followersIds = friendshipRepository.findUserIdsByFriendId(userId);

        // ✅ 3. Usuarios con amistad aceptada (mutuos)
        List<Long> mutualIds = friendshipRepository.findAcceptedFriendIdsByUserId(userId);

        // ✅ 4. Solicitudes pendientes (enviadas o recibidas)
        List<Long> pendingIds = friendshipRepository.findPendingFriendshipUserIds(userId);

        // ✅ 5. Amigos de mis amigos (solo aceptadas)
        List<Long> friendsOfFriends = friendshipRepository.findAcceptedFriendIdsByUserIds(mutualIds).stream()
                .filter(id -> !id.equals(userId))
                .filter(id -> !mutualIds.contains(id))
                .distinct()
                .toList();

        // ✅ 6. Usuarios que mis amigos siguen
        List<Long> friendsFollowing = friendshipRepository.findFollowingOfFriends(mutualIds).stream()
                .filter(id -> !id.equals(userId))
                .filter(id -> !mutualIds.contains(id))
                .filter(id -> !friendsOfFriends.contains(id))
                .distinct()
                .toList();

        // ✅ 7. Jugadores más activos (más reservas confirmadas)
        List<Long> topPlayers = reservationRepository.findTopPlayersByConfirmedReservations().stream()
                .filter(id -> !id.equals(userId))
                .filter(id -> !mutualIds.contains(id))
                .filter(id -> !friendsOfFriends.contains(id))
                .filter(id -> !friendsFollowing.contains(id))
                .toList();

        // ✅ 8. Combinar sugerencias
        List<Long> allSuggestedIds = new ArrayList<>();
        allSuggestedIds.addAll(friendsOfFriends);
        allSuggestedIds.addAll(friendsFollowing);
        allSuggestedIds.addAll(topPlayers);

        // ✅ 9. Excluir los que YA SIGO o tengo solicitud pendiente
        // ⚠️ (NO excluye los que me siguen)
        allSuggestedIds = allSuggestedIds.stream()
                .filter(id -> !followingIds.contains(id))
                .filter(id -> !pendingIds.contains(id))
                .distinct()
                .toList();

        // ✅ 10. Buscar usuarios y mantener orden
        List<User> suggestedUsers = userRepository.findAllById(allSuggestedIds);
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < allSuggestedIds.size(); i++) {
            orderMap.put(allSuggestedIds.get(i), i);
        }

        suggestedUsers.sort(Comparator.comparingInt(u -> orderMap.getOrDefault(u.getId(), Integer.MAX_VALUE)));

        return suggestedUsers.stream()
                .map(this::toDTO)
                .toList();
    }



    public List<UserDTO> findAvailablePlayers() {
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        return activeUsers.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    // 🔹 Eliminar usuario
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /** 🔹 Obtener los amigos mutuos (ambos ACCEPTED) */
    public List<UserDTO> getFriends(Long userId) {
        // Usuarios que yo sigo con estado ACCEPTED
        List<Long> followingIds = friendshipRepository.findAcceptedFriendIdsByUserId(userId);

        // Usuarios que me siguen con estado ACCEPTED
        List<Long> followersIds = friendshipRepository.findAcceptedUserIdsByFriendId(userId);

        // Intersección → amigos mutuos
        List<Long> mutualIds = followingIds.stream()
                .filter(followersIds::contains)
                .toList();

        // Buscar usuarios y mapear a DTO
        List<User> mutualUsers = userRepository.findAllById(mutualIds);
        return mutualUsers.stream()
                .map(this::toDTO)
                .toList();
    }

    public User updateProfileImage(Long userId, String imageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setProfileImageUrl(imageUrl);
        return userRepository.save(user);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }


    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setProfileImageUrl(user.getProfileImageUrl());
        dto.setStatus(String.valueOf(user.getStatus()));
        return dto;
    }

    private InfoUserDTO toDTOinfo(User user) {
        InfoUserDTO dto = new InfoUserDTO();
        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setProfileImageUrl(user.getProfileImageUrl());
        return dto;
    }



}
