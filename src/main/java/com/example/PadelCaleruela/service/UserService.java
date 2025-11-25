package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.AppProperties;
import com.example.PadelCaleruela.dto.InfoUserDTO;
import com.example.PadelCaleruela.dto.PlayerInfoDTO;
import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
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

    private final LeagueRepository leagueRepository;

    private final AuthService authService;

    private final AppProperties appProperties;

    private final FollowRepository followRepository;

    private final AyuntamientoRepository ayuntamientoRepository;


    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/profile-images/";


    public UserService(
            UserRepository repo,
            BCryptPasswordEncoder passwordEncoder,
            FriendshipRepository friendshipRepository,
            ReservationRepository reservationRepository,
            EmailService emailService,
            LeagueRepository leagueRepository,
            AuthService authService,
            AppProperties appProperties,
            FollowRepository followRepository,
            AyuntamientoRepository ayuntamientoRepository
    ) {
        this.userRepository = repo;
        this.passwordEncoder = passwordEncoder;
        this.friendshipRepository = friendshipRepository;
        this.reservationRepository = reservationRepository;
        this.emailService = emailService;
        this.leagueRepository = leagueRepository;
        this.authService = authService;
        this.appProperties = appProperties;
        this.followRepository=followRepository;
        this.ayuntamientoRepository=ayuntamientoRepository;
    }


    public List<UserDTO> getAllUsers() {

        User current = authService.getCurrentUser();

        // SUPERADMIN → ver todo
        if (authService.isSuperAdmin()) {
            return userRepository.findAll()
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        // ADMIN → ver solo su ayuntamiento
        if (authService.isAdmin()) {

            if (current.getAyuntamiento() == null) {
                throw new IllegalStateException("El administrador no tiene ayuntamiento asignado.");
            }

            Long ayuntamientoId = current.getAyuntamiento().getId();

            return userRepository.findByAyuntamientoId(ayuntamientoId)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        // USER → prohibido
        throw new AccessDeniedException("No tienes permisos para ver la lista de usuarios.");
    }



    public List<InfoUserDTO> getAllInfoUsers() {

        User current = authService.getCurrentUser();

        // SUPERADMIN → ver todos
        if (authService.isSuperAdmin()) {
            return userRepository.findAll()
                    .stream()
                    .map(this::toDTOinfo)
                    .toList();
        }

        // ADMIN → solo los de su ayuntamiento
        if (authService.isAdmin()) {

            if (current.getAyuntamiento() == null) {
                throw new IllegalStateException("El administrador no tiene ayuntamiento asignado.");
            }

            Long ayId = current.getAyuntamiento().getId();

            return userRepository.findByAyuntamientoId(ayId)
                    .stream()
                    .map(this::toDTOinfo)
                    .toList();
        }

        // USER → prohibido
        throw new AccessDeniedException("No tienes permiso para ver esta información.");
    }



    @Transactional
    public User updateUserRole(Long userId, String newRole) {

        User current = authService.getCurrentUser();

        // Validar existente
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Validar rol destino
        if (!newRole.equalsIgnoreCase("USER") &&
                !newRole.equalsIgnoreCase("ADMIN") &&
                !newRole.equalsIgnoreCase("SUPERADMIN")) {

            throw new IllegalArgumentException("Rol no válido.");
        }

        Role targetRole = Role.valueOf(newRole.toUpperCase());

        // ============================
        // SUPERADMIN → puede todo
        // ============================
        if (authService.isSuperAdmin()) {
            target.setRole(targetRole);
            return userRepository.save(target);
        }

        // ============================
        // ADMIN → restricciones
        // ============================

        if (authService.isAdmin()) {

            // 1) No tiene ayuntamiento? No debería pasar.
            if (current.getAyuntamiento() == null) {
                throw new IllegalStateException("Tu cuenta no tiene ayuntamiento asignado.");
            }

            Long adminAyto = current.getAyuntamiento().getId();

            // 2) Solo puede modificar usuarios de su ayuntamiento
            if (target.getAyuntamiento() == null ||
                    !target.getAyuntamiento().getId().equals(adminAyto)) {
                throw new AccessDeniedException("No puedes modificar usuarios de otro ayuntamiento.");
            }

            // 3) Un ADMIN no puede ascender a SUPERADMIN
            if (targetRole == Role.SUPERADMIN) {
                throw new AccessDeniedException("No puedes asignar rol SUPERADMIN.");
            }

            // 4) Un ADMIN no puede modificar a un SUPERADMIN
            if (target.getRole() == Role.SUPERADMIN) {
                throw new AccessDeniedException("No puedes modificar a un SUPERADMIN.");
            }

            // 5) Un ADMIN puede modificar a USER y ADMIN dentro de su ayuntamiento
            target.setRole(targetRole);
            return userRepository.save(target);
        }

        // ============================
        // USER → prohibido
        // ============================
        throw new AccessDeniedException("No tienes permisos para cambiar roles.");
    }



    public UserDTO saveUser(User user) {

        User current = authService.getCurrentUser();

        // ===============================
        // 🚫 NADIE puede crear SUPERADMIN
        // ===============================
        if (user.getRole() == Role.SUPERADMIN) {
            throw new AccessDeniedException("No está permitido crear usuarios con rol SUPERADMIN.");
        }

        // ======================================================
        // ADMIN → solo puede crear usuarios dentro de su ayto
        // ======================================================
        if (authService.isAdmin()) {

            if (user.getAyuntamiento() == null ||
                    current.getAyuntamiento() == null ||
                    !Objects.equals(
                            user.getAyuntamiento().getId(),
                            current.getAyuntamiento().getId()
                    )) {

                throw new AccessDeniedException(
                        "Un administrador solo puede crear usuarios en su ayuntamiento."
                );
            }

            // 🚫 ADMIN NO puede crear administradores. Solo USER.
            if (user.getRole() == Role.ADMIN) {
                throw new AccessDeniedException(
                        "Un administrador no puede crear otros administradores."
                );
            }
        }

        // ======================================================
        // USER → prohibido crear usuarios
        // ======================================================
        if (authService.isUser()) {
            throw new AccessDeniedException("No tienes permisos para crear usuarios.");
        }

        // ===============================
        // Validación de username y email
        // ===============================
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }

        // ======================================================
        // Generar o cifrar contraseña
        // ======================================================
        String pass = "";

        if (user.getPassword().isEmpty()) {

            final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            SecureRandom random = new SecureRandom();
            StringBuilder sb = new StringBuilder(8);

            for (int i = 0; i < 8; i++) {
                sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
            }

            pass = sb.toString();
            user.setPassword(passwordEncoder.encode(pass));

        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        // ===============================
        // Guardar usuario
        // ===============================
        User saved = userRepository.save(user);

        // ===============================
        // Enviar email con contraseña
        // ===============================
        emailService.sendHtmlEmail(
                user.getEmail(),
                "Usuario creado correctamente",
                "<h3>¡Hola " + user.getUsername() + "!</h3>" +
                        "<p>Bienvenido a la mejor aplicación de pádel del mundo 🎾.</p>" +
                        "<p>Se te ha asignado una contraseña temporal, puedes cambiarla desde la app.</p>" +
                        "<p><strong>Contraseña: " + pass + "</strong></p>"
        );

        return toDTO(saved);
    }

    // ⬇️ Dentro de UserService
    public UserDTO updateUserAyuntamiento(Long userId, Long ayuntamientoId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        Ayuntamiento ayto = ayuntamientoRepository.findById(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado."));

        user.setAyuntamiento(ayto);
        userRepository.save(user);

        return toDTO(user);
    }




    public UserDTO getUserById(Long id) {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // SUPERADMIN → permitido
        if (authService.isSuperAdmin()) return toDTO(target);

        // USER → solo puede verse a sí mismo
        if (authService.isUser() && !current.getId().equals(id)) {
            throw new AccessDeniedException("No puedes ver datos de otro usuario.");
        }

        // ADMIN → solo usuarios de su ayuntamiento
        authService.ensureSameAyuntamiento(target);

        return toDTO(target);
    }

    public PlayerInfoDTO getPublicPlayerProfile(Long id) {

        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Si quieres limitar por ayuntamiento para TODOS menos superadmin:
        User current = authService.getCurrentUser();
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(target);
        }

        // Aquí devuelves solo info pública
        return toPlayerInfoDTO(target); // o como lo tengas mapeado
    }



    // 🔍 Buscar usuarios por username (insensible a mayúsculas/minúsculas)
    public List<UserDTO> searchUsersByUsername(String username) {

        User current = authService.getCurrentUser();

        List<User> results = userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(username, username);

        if (authService.isSuperAdmin()) {
            return results.stream().map(this::toDTO).toList();
        }

        // Filtrar por ayuntamiento (ADMIN y USER)
        Long ayId = current.getAyuntamiento().getId();

        return results.stream()
                .filter(u -> Objects.equals(u.getAyuntamiento().getId(), ayId))
                .map(this::toDTO)
                .toList();
    }


    public UserDTO updateUserProfile(Long id, String fullName, String username,
                                     String email, String password, MultipartFile profileImage) throws IOException {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // SUPERADMIN → puede editar a cualquiera
        if (!authService.isSuperAdmin()) {

            // USER → solo a sí mismo
            if (authService.isUser() && !current.getId().equals(id)) {
                throw new AccessDeniedException("No puedes editar el perfil de otro usuario.");
            }

            // ADMIN → solo usuarios de su ayuntamiento
            authService.ensureSameAyuntamiento(target);
        }

        // --- Actualizaciones seguras ---
        if (fullName != null && !fullName.isBlank()) target.setFullName(fullName);
        if (username != null && !username.isBlank()) target.setUsername(username);
        if (email != null && !email.isBlank()) target.setEmail(email);
        if (password != null && !password.isBlank()) {
            target.setPassword(passwordEncoder.encode(password));
        }
        if (profileImage != null && !profileImage.isEmpty()) {
            String imageUrl = saveProfileImage(profileImage);
            target.setProfileImageUrl(imageUrl);
        }

        return toDTO(userRepository.save(target));
    }


    // ✅ Actualizar el estado del usuario
    @Transactional
    public void updateUserStatus(Long userId, String newStatus) {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!authService.isSuperAdmin()) {

            if (authService.isUser() && !current.getId().equals(userId)) {
                throw new AccessDeniedException("No puedes cambiar el estado de otro usuario.");
            }

            authService.ensureSameAyuntamiento(target);
        }

        target.setStatus(UserStatus.valueOf(newStatus.toUpperCase()));
        userRepository.save(target);
    }



    private String saveProfileImage(MultipartFile file) throws IOException {

        // Crear carpeta si no existe
        Path uploadPath = Paths.get("uploads/profile-images");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Sanear nombre
        String originalFileName = file.getOriginalFilename();
        String sanitizedFileName = originalFileName != null
                ? originalFileName.replaceAll("\\s+", "_")
                : "unknown";

        // Nombre único
        String filename = UUID.randomUUID() + "_" + sanitizedFileName;
        Path filePath = uploadPath.resolve(filename);

        // Guardar archivo físico
        Files.write(filePath, file.getBytes());

        // URL pública completa
        return appProperties.getBaseUrl() + "/uploads/profile-images/" + filename;
    }



    // 🔹 Actualizar usuario
    public Optional<UserDTO> updateUser(Long id, User updatedUser) {

        User current = authService.getCurrentUser();

        // SUPERADMIN → full access
        if (authService.isUser()) {
            throw new AccessDeniedException("No tienes permiso para modificar usuarios.");
        }

        return userRepository.findById(id).map(user -> {

            // ADMIN → solo su ayuntamiento
            if (authService.isAdmin()) {
                authService.ensureSameAyuntamiento(user);
            }

            // Validaciones normales…
            if (!user.getUsername().equals(updatedUser.getUsername()) &&
                    userRepository.findByUsername(updatedUser.getUsername()).isPresent()) {
                throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
            }

            if (!user.getEmail().equals(updatedUser.getEmail()) &&
                    userRepository.findByEmail(updatedUser.getEmail()).isPresent()) {
                throw new IllegalArgumentException("El correo ya está en uso.");
            }

            user.setUsername(updatedUser.getUsername());
            user.setEmail(updatedUser.getEmail());
            user.setFullName(updatedUser.getFullName());

            if (updatedUser.getPassword() != null &&
                    !updatedUser.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
            }

            return toDTO(userRepository.save(user));
        });
    }

    public boolean isFollowing(Long followerId, Long targetId) {
        return followRepository.existsByFollowerIdAndFollowedId(followerId, targetId);
    }

    /**
     * 🔹 Obtiene los amigos de mis amigos (sugerencias)
     */
    public List<UserDTO> getSuggestedPlayers(Long userId) {

        User current = authService.getCurrentUser();

        // 🔐 Solo SUPERADMIN puede ver sugerencias de otros usuarios
        if (!authService.isSuperAdmin() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes obtener sugerencias para otro usuario.");
        }

        Long ayId = current.getAyuntamiento().getId();

        // --------------------------------------------------------------------
        // 🔥 Lógica original de sugerencias
        // --------------------------------------------------------------------

        // 1️⃣ Usuarios que yo sigo (amistades aceptadas o pendientes)
        List<Long> followingIds = friendshipRepository.findFriendIdsByUserId(userId);

        // 2️⃣ Usuarios que me siguen
        List<Long> followersIds = friendshipRepository.findUserIdsByFriendId(userId);

        // 3️⃣ Amistad mutua (ACCEPTED en ambos lados)
        List<Long> mutualIds = friendshipRepository.findAcceptedFriendIdsByUserId(userId);

        // 4️⃣ Solicitudes pendientes
        List<Long> pendingIds = friendshipRepository.findPendingFriendshipUserIds(userId);

        // 5️⃣ Amigos de mis amigos (ACCEPTED)
        List<Long> friendsOfFriends = friendshipRepository.findAcceptedFriendIdsByUserIds(mutualIds)
                .stream()
                .filter(id -> !id.equals(userId))
                .filter(id -> !mutualIds.contains(id))
                .distinct()
                .toList();

        // 6️⃣ Usuarios que mis amigos siguen
        List<Long> friendsFollowing = friendshipRepository.findFollowingOfFriends(mutualIds)
                .stream()
                .filter(id -> !id.equals(userId))
                .filter(id -> !mutualIds.contains(id))
                .filter(id -> !friendsOfFriends.contains(id))
                .distinct()
                .toList();

        // 7️⃣ Jugadores más activos (reservas confirmadas)
        List<Long> topPlayers = reservationRepository.findTopPlayersByConfirmedReservations()
                .stream()
                .filter(id -> !id.equals(userId))
                .filter(id -> !mutualIds.contains(id))
                .filter(id -> !friendsOfFriends.contains(id))
                .filter(id -> !friendsFollowing.contains(id))
                .toList();

        // 8️⃣ Combinar todas las sugerencias
        List<Long> allSuggestedIds = new ArrayList<>();
        allSuggestedIds.addAll(friendsOfFriends);
        allSuggestedIds.addAll(friendsFollowing);
        allSuggestedIds.addAll(topPlayers);

        // 9️⃣ Excluir los que YA SIGO o tienen solicitud pendiente
        allSuggestedIds = allSuggestedIds.stream()
                .filter(id -> !followingIds.contains(id))
                .filter(id -> !pendingIds.contains(id))
                .distinct()
                .toList();

        // 🔟 Cargar usuarios manteniendo el orden original
        List<User> suggestedUsers = userRepository.findAllById(allSuggestedIds);
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < allSuggestedIds.size(); i++) {
            orderMap.put(allSuggestedIds.get(i), i);
        }

        suggestedUsers.sort(Comparator.comparingInt(
                u -> orderMap.getOrDefault(u.getId(), Integer.MAX_VALUE)
        ));

        // --------------------------------------------------------------------
        // 🔐 FILTRO MULTI-AYUNTAMIENTO
        // --------------------------------------------------------------------
        // SUPERADMIN → ve todos
        if (!authService.isSuperAdmin()) {
            suggestedUsers = suggestedUsers.stream()
                    .filter(u -> u.getAyuntamiento() != null &&
                            Objects.equals(u.getAyuntamiento().getId(), ayId))
                    .toList();
        }

        // --------------------------------------------------------------------
        // 🎯 Convertir a DTO y devolver
        // --------------------------------------------------------------------
        return suggestedUsers.stream()
                .map(this::toDTO)
                .toList();
    }




    public List<UserDTO> findAvailablePlayers() {

        User current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) {
            return userRepository.findByStatus(UserStatus.ACTIVE)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        Long ayId = current.getAyuntamiento().getId();

        return userRepository.findByStatus(UserStatus.ACTIVE)
                .stream()
                .filter(u -> Objects.equals(u.getAyuntamiento().getId(), ayId))
                .map(this::toDTO)
                .toList();
    }


    /**
     * Devuelve todos los usuarios que NO estén inscritos en una liga específica.
     */
    public List<PlayerInfoDTO> getAvailableUsersForLeague(Long leagueId) {

        User current = authService.getCurrentUser();
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("Liga no encontrada"));

        // 🔐 Validar que la liga pertenece al mismo ayuntamiento
        authService.ensureSameAyuntamiento(league.getAyuntamiento());

        Set<Long> playerIdsInLeague = league.getPlayers()
                .stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        List<User> allUsers = userRepository.findByAyuntamientoId(
                league.getAyuntamiento().getId()
        );

        return allUsers.stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> !playerIdsInLeague.contains(u.getId()))
                .map(u -> new PlayerInfoDTO(
                        u.getId(),
                        u.getUsername(),
                        u.getProfileImageUrl(),
                        false,
                        u.getStatus()
                ))
                .toList();
    }




    // 🔹 Eliminar usuario
    public boolean deleteUser(Long id) {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (authService.isUser()) {
            throw new AccessDeniedException("No puedes eliminar usuarios.");
        }

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target);
        }

        userRepository.delete(target);
        return true;
    }


    /** 🔹 Obtener los amigos mutuos (ambos ACCEPTED) */
    public List<UserDTO> getFriends(Long userId) {

        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver los amigos de otro usuario.");
        }

        List<Long> followingIds = friendshipRepository.findAcceptedFriendIdsByUserId(userId);
        List<Long> followersIds = friendshipRepository.findAcceptedUserIdsByFriendId(userId);

        List<Long> mutual = followingIds.stream()
                .filter(followersIds::contains)
                .toList();

        List<User> users = userRepository.findAllById(mutual);

        return users.stream().map(this::toDTO).toList();
    }


    @Transactional
    public List<PlayerInfoDTO> getAvailablePlayersForReservation(Long reservationId, Long requesterId) {

        User requester = authService.getCurrentUser();
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        // 🔐 Validación multi-ayuntamiento
        authService.ensureSameAyuntamiento(reservation.getAyuntamiento());

        // 👴 Solo superadmin puede ignorar requesterId
        if (!authService.isSuperAdmin() && !Objects.equals(requester.getId(), requesterId)) {
            throw new AccessDeniedException("No tienes permiso para hacer esta acción.");
        }

        Set<Long> jugadoresActuales = reservation.getJugadores().stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        Set<Long> excluidosPorInvitacion = reservation.getInvitations().stream()
                .filter(inv -> inv.getStatus() != InvitationStatus.ACCEPTED)
                .map(inv -> inv.getReceiver().getId())
                .collect(Collectors.toSet());

        Set<Long> excluidos = new HashSet<>(jugadoresActuales);
        excluidos.addAll(excluidosPorInvitacion);
        excluidos.add(reservation.getUser().getId()); // creador

        // Traemos todos los usuarios del mismo ayuntamiento
        List<User> validUsers = userRepository.findByAyuntamientoId(
                reservation.getAyuntamiento().getId()
        );

        return validUsers.stream()
                .filter(u -> !excluidos.contains(u.getId()))
                .map(u -> new PlayerInfoDTO(
                        u.getId(),
                        u.getUsername(),
                        u.getProfileImageUrl(),
                        false,
                        u.getStatus()
                ))
                .toList();
    }



    public User updateProfileImage(Long userId, String imageUrl) {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes cambiar la foto de otro usuario.");
        }

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target);
        }

        target.setProfileImageUrl(imageUrl);
        return userRepository.save(target);
    }


    public User findByUsername(String username) {

        User current = authService.getCurrentUser();
        User found = userRepository.findByUsername(username).orElse(null);

        if (found == null) return null;

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(found);
        }

        return found;
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

    private PlayerInfoDTO toPlayerInfoDTO(User user) {

        return new PlayerInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getProfileImageUrl(),
                false,
                user.getStatus()
        );
    }


}
