package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.AuthResponse;
import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.dto.UserRegister;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.UserRepository;
import com.example.PadelCaleruela.CustomUserDetails;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final AyuntamientoService ayuntamientoService;
    private final NotificationFactory notificationFactory;
    private final NotificationAppService notificationAppService;

    /** 🔹 Registro de usuario */
    public AuthResponse register(UserRegister dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        if (dto.getEmail() == null || !dto.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("❌ Dirección de correo no válida: " + dto.getEmail());
        }
        System.out.println("Parte 2");

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setFullName(dto.getFullName());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.USER);
        user.setProfileImageUrl("https://ui-avatars.com/api/?name="
                + user.getUsername()
                + "&background=0D8ABC&color=fff");
        user.setStatus(UserStatus.ACTIVE);
        System.out.println("Parte 3");

        Ayuntamiento ayuntamiento = ayuntamientoService.findByCodigoPostal(dto.getCodigoPostal());
        System.out.println("Parte 4");
        user.setAyuntamiento(ayuntamiento);
        userRepository.save(user);
        System.out.println("Parte 5");
        emailService.sendHtmlEmail(
                user.getEmail(),
                "Registro completado",
                "<h3>¡Hola " + user.getUsername() + "!</h3>" +
                        "<p>Bienvenido a la mejor aplicación de pádel del mundo 🎾.</p>"
        );
        System.out.println("Parte 2");

        // ---------------------------------------------------------
        // 🔔 NOTIFICACIÓN A TODOS LOS ADMINS DEL AYUNTAMIENTO
        // ---------------------------------------------------------
        List<User> admins = userRepository.findByAyuntamientoIdAndRole(ayuntamiento.getId(), Role.ADMIN);

        for (User admin : admins) {
            try {
                Notification notification = new Notification();
                notification.setUserId(admin.getId());             // receptor → admin
                notification.setSenderId(user.getId());            // remitente → el nuevo usuario
                notification.setType(NotificationType.ADMIN_USER_REGISTERED);
                notification.setTitle(notificationFactory.getTitle(NotificationType.ADMIN_USER_REGISTERED));
                notification.setMessage(notificationFactory.getMessage(NotificationType.ADMIN_USER_REGISTERED, user.getFullName()));
                notification.setReadFlag(false);
                notification.setCreatedAt(LocalDateTime.now());

                notificationAppService.saveNotification(notification);

            } catch (Exception e) {
                System.out.println("⚠ Error guardando notificación de registro para admin " + admin.getId() + ": " + e.getMessage());
            }
        }

        // ---------------------------------------------------------


        // Generar token JWT
        var userDetails = new CustomUserDetails(user);
        var token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                ayuntamiento.getId()
        );


    }

    public Long getAyuntamientoId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || auth.getPrincipal().equals("anonymousUser")) {
            return null;
        }

        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        return user.getAyuntamientoId();
    }


    /** 🔹 Login de usuario */
    public AuthResponse login(Map<String, String> request) {

        String email = request.get("email");
        String password = request.get("password");

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        var userDetails = new CustomUserDetails(user);
        var token = jwtService.generateToken(userDetails);

        String role = user.getRole() != null ? user.getRole().name() : "USER";

        Long ayto = null;

        // 🔹 Usuarios normales -> deben tener ayto
        if (user.getRole() != Role.SUPERADMIN) {

            if (user.getAyuntamiento() == null) {
                throw new IllegalStateException("El usuario no tiene un ayuntamiento asignado");
            }

            ayto = user.getAyuntamiento().getId();
        }

        return new AuthResponse(token, user.getId(), user.getUsername(), user.getEmail(), role, ayto);
    }


    public User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("No autenticado");
        }

        Object principal = auth.getPrincipal();

        // ✔️ Caso 1: UserDetails (lo más común)
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            return userRepository.findByUsernameOrEmail(ud.getUsername())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        }

        // ✔️ Caso 2: JWT donde el "name" es email/username/sub
        String key = auth.getName();  // email, username o subject

        return userRepository.findByUsernameOrEmail(key)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public Long getAyuntamientoIdFromJwt(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);

        // <- Usa tu servicio JWT actual
        return jwtService.extractClaim(token, claims -> claims.get("ayuntamientoId", Long.class));
    }


    public boolean isSuperAdmin() {
        return getCurrentUser().getRole() == Role.SUPERADMIN;
    }

    public boolean isAdmin() {
        return getCurrentUser().getRole() == Role.ADMIN;
    }

    public boolean isUser() {
        return getCurrentUser().getRole() == Role.USER;
    }

    public void ensureSameAyuntamiento(User targetUser) {
        User current = getCurrentUser();

        // superadmin puede saltarse esta regla
        if (current.getRole() == Role.SUPERADMIN) return;

        if (!Objects.equals(current.getAyuntamiento().getId(), targetUser.getAyuntamiento().getId())) {
            throw new AccessDeniedException("No tienes permisos para acceder a este usuario de otro ayuntamiento");
        }
    }

    public void ensureSameAyuntamiento(Ayuntamiento ayuntamiento) {
        User current = getCurrentUser();
        if (current.getRole() == Role.SUPERADMIN) return;

        if (!Objects.equals(current.getAyuntamiento().getId(), ayuntamiento.getId())) {
            throw new AccessDeniedException("No tienes permiso para acceder a datos de otro ayuntamiento");
        }
    }


}
