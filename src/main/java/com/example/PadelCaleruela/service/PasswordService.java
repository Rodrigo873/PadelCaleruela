package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.PasswordResetToken;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.PasswordResetTokenRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    /**
     * 1️⃣ El usuario solicita recuperación de contraseña
     * Solo él mismo puede solicitar su propio código.
     */
    @Transactional
    public void sendResetCode(String email) throws MessagingException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        User current = authService.getCurrentUser();

        // SUPERADMIN puede resetear de cualquiera
        if (!authService.isSuperAdmin()) {
            if (!current.getId().equals(user.getId())) {
                throw new AccessDeniedException("No puedes solicitar un código para otro usuario.");
            }
        }

        // Eliminar tokens anteriores
        tokenRepository.deleteAllByUser(user);

        // Generar código de 6 dígitos
        String code = String.format("%06d", new Random().nextInt(999999));

        PasswordResetToken token = PasswordResetToken.builder()
                .token(code)
                .user(user)
                .expiration(LocalDateTime.now().plusMinutes(10))
                .build();

        tokenRepository.save(token);

        // Enviar correo
        String html = """
            <div style="font-family: Arial, sans-serif; color: #333;">
                <h2>Recuperación de contraseña 🔐</h2>
                <p>Hola %s,</p>
                <p>Tu código de verificación es:</p>
                <h3 style="color:#0b5ed7;">%s</h3>
                <p>El código caduca en 10 minutos.</p>
            </div>
            """.formatted(user.getUsername(), code);

        emailService.sendHtmlEmail(user.getEmail(), "Código de recuperación", html);
    }

    /**
     * 2️⃣ Verificación del código
     * VALIDAMOS que el código pertenece al usuario autenticado.
     */
    public boolean verifyCode(String code) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(code);
        if (tokenOpt.isEmpty()) return false;

        PasswordResetToken token = tokenOpt.get();

        User current = authService.getCurrentUser();

        // SUPERADMIN puede validarlo para cualquiera
        if (!authService.isSuperAdmin()) {
            if (!token.getUser().getId().equals(current.getId())) {
                throw new AccessDeniedException("No puedes validar un código que no es tuyo.");
            }
        }

        if (token.getExpiration().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(token);
            return false;
        }

        return true;
    }

    /**
     * 3️⃣ Cambio de contraseña
     * También validamos identidad estrictamente.
     */
    @Transactional
    public void resetPassword(String code, String newPassword) {
        PasswordResetToken token = tokenRepository.findByToken(code)
                .orElseThrow(() -> new RuntimeException("Código no válido o expirado."));

        User targetUser = token.getUser();
        User current = authService.getCurrentUser();

        // SUPERADMIN puede cambiar contraseñas de cualquier usuario
        if (!authService.isSuperAdmin()) {
            if (!current.getId().equals(targetUser.getId())) {
                throw new AccessDeniedException("No puedes cambiar la contraseña de otro usuario.");
            }
        }

        if (token.getExpiration().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(token);
            throw new RuntimeException("El código ha expirado.");
        }

        // Cambiar contraseña
        targetUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(targetUser);

        // Eliminar token usado
        tokenRepository.delete(token);
    }
}
