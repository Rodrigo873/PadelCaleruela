package com.example.PadelCaleruela;

import com.example.PadelCaleruela.model.Role;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.UserStatus;
import com.example.PadelCaleruela.repository.UserRepository;
import com.example.PadelCaleruela.service.EmailService;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Override
    public void run(String... args) {
        // Si no existe ningún administrador, crear uno
        if (userRepository.findByUsername("superadmin").isEmpty()) {
            User admin = new User();
            admin.setUsername("superadmin");
            admin.setFullName("Super Administrador Padel");
            admin.setEmail("rodrigorinconparra@gmail.com");
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
            // 🔐 Cifrar antes de guardar
            admin.setPassword(passwordEncoder.encode(generatedPassword));

            admin.setRole(Role.SUPERADMIN);
            admin.setStatus(UserStatus.OFFLINE);

            userRepository.save(admin);
            String html =
                    "<html><body>" +
                            "<h3>¡Hola " + admin.getUsername() + "!</h3>" +
                            "<p>Bienvenido a la mejor aplicación de pádel del mundo 🎾.</p>" +
                            "<p>Se te ha asignado una contraseña al azar, puedes cambiarla desde la app.</p>" +
                            "<p>La contraseña es: <strong>" + generatedPassword + "</strong></p>" +
                            "</body></html>";

            emailService.sendHtmlEmail(
                    admin.getEmail(),
                    "Usuario super admin creado correctamente",
                    html
            );


        } else {
        }
    }
}
