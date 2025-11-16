package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.AuthResponse;
import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.dto.UserRegister;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Role;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.UserStatus;
import com.example.PadelCaleruela.repository.UserRepository;
import com.example.PadelCaleruela.security.CustomUserDetails;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final AyuntamientoService ayuntamientoService;

    /** 🔹 Registro de usuario */
    public AuthResponse register(UserRegister dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        if (dto.getEmail() == null || !dto.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("❌ Dirección de correo no válida: " + dto.getEmail());
        }
        User user=new User();
        user.setEmail(dto.getEmail());
        user.setFullName(dto.getFullName());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.USER);
        user.setProfileImageUrl("https://ui-avatars.com/api/?name="
                + user.getUsername()
                + "&background=0D8ABC&color=fff");
        user.setStatus(UserStatus.ACTIVE);
        Ayuntamiento ayuntamiento = ayuntamientoService.findByCodigoPostal(dto.getCodigoPostal());
        user.setAyuntamiento(ayuntamiento);
        userRepository.save(user);

        emailService.sendHtmlEmail(
                user.getEmail(),
                "Registro completado",
                "<h3>¡Hola " + user.getUsername() + "!</h3>" +
                        "<p>Bienvenido a la mejor aplicación de pádel del mundo 🎾.</p>"
        );

        // Generar token JWT
        var userDetails = new CustomUserDetails(user);
        var token = jwtService.generateToken(userDetails);

        return new AuthResponse(token, user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
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

        return new AuthResponse(token, user.getId(), user.getUsername(), user.getEmail(), role);
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
