package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.AuthResponse;
import com.example.PadelCaleruela.model.Role;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.UserStatus;
import com.example.PadelCaleruela.repository.UserRepository;
import com.example.PadelCaleruela.security.CustomUserDetails;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    /** 🔹 Registro de usuario */
    public AuthResponse register(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        if (user.getEmail() == null || !user.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("❌ Dirección de correo no válida: " + user.getEmail());
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(Role.USER);
        user.setProfileImageUrl("https://ui-avatars.com/api/?name="
                + user.getUsername()
                + "&background=0D8ABC&color=fff");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        try {
            emailService.sendHtmlEmail(
                    user.getEmail(),
                    "Registro completado",
                    "<h3>¡Hola " + user.getUsername() + "!</h3>" +
                            "<p>Bienvenido a la mejor aplicación de pádel del mundo 🎾.</p>"
            );
        } catch (MessagingException e) {
            System.err.println("⚠️ Error al enviar el correo: " + e.getMessage());
        }

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
}
