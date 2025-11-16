package com.example.PadelCaleruela.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    // Remitente con nombre "PadelApp"
    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.fromName:PadelApp}")
    private String fromName;

    // Límite anti-spam (tamaño máximo del HTML)
    private static final int MAX_EMAIL_SIZE = 100_000; // 100 KB

    // Validación anti header-injection
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");


    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ======================================================
    // VALIDACIONES
    // ======================================================

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Email destino inválido.");
        }
        if (email.contains("\n") || email.contains("\r")) {
            throw new IllegalArgumentException("Email contiene caracteres no permitidos.");
        }
    }

    private void validateHtml(String html) {
        if (html == null) return;
        if (html.length() > MAX_EMAIL_SIZE) {
            throw new IllegalArgumentException("Contenido HTML demasiado grande.");
        }
    }


    // ======================================================
    // EMAIL TEXTO PLANO
    // ======================================================

    public void sendSimpleEmail(String to, String subject, String text) {
        validateEmail(to);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", fromName, from));
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("📨 Email simple enviado a {}", to);

        } catch (MailException ex) {
            log.error("❌ Error enviando email simple a {}: {}", to, ex.getMessage());
            // No exponemos detalles internos
            throw new RuntimeException("No se pudo enviar el correo.");
        }
    }


    // ======================================================
    // EMAIL HTML (asíncrono seguro)
    // ======================================================

    @Async
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        validateEmail(to);
        validateHtml(htmlContent);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(String.format("%s <%s>", fromName, from));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);

            log.info("📨 Email HTML enviado a {}", to);

        } catch (MessagingException | MailException ex) {
            log.error("❌ Error enviando email HTML a {}: {}", to, ex.getMessage());
            throw new RuntimeException("No se pudo enviar el correo HTML.");
        }
    }
}
