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

            // 🌟 URL DEL LOGO (como me diste)
            String logoUrl = "http://192.168.1.62:8080/uploads/posts/2/b8321976-0677-408f-a041-7f69d06d5a21_LogoBoostPlay.jpg";

            // ===============================
            // ✨ TEMPLATE PROFESIONAL
            // ===============================
            String emailTemplate = """
                <div style="font-family: Arial, Helvetica, sans-serif; background-color: #f6f8fb; padding: 30px;">
                
                    <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 12px;
                                box-shadow: 0 4px 12px rgba(0,0,0,0.1); overflow: hidden;">
                                
                        <!-- HEADER -->
                        <div style="background: #111827; padding: 20px; text-align: center;">
                            <img src="%s" alt="BoostPlay" style="max-height: 70px; border-radius: 8px;" />
                        </div>

                        <!-- CONTENT -->
                        <div style="padding: 30px; font-size: 16px; color: #333;">
                            %s
                        </div>

                        <!-- FOOTER -->
                        <div style="background: #f1f5f9; padding: 20px; text-align: center; font-size: 13px; color: #6b7280;">
                            <p style="margin: 0; font-weight: bold; color: #111827;">BoostPlay</p>
                            <p style="margin: 5px 0;">El motor que impulsa tu comunidad deportiva.</p>
                            <p style="margin: 5px 0;">📧 boostplay4@gmail.com</p>
                            <p style="font-size: 12px; color: #94a3b8; margin-top: 10px;">
                                © %d BoostPlay. Todos los derechos reservados.
                            </p>
                        </div>
                    </div>
                </div>
                """.formatted(logoUrl, htmlContent, java.time.Year.now().getValue());

            helper.setText(emailTemplate, true);

            mailSender.send(mimeMessage);

            log.info("📨 Email HTML enviado a {}", to);

        } catch (MessagingException | MailException ex) {
            log.error("❌ Error enviando email HTML a {}: {}", to, ex.getMessage());
            throw new RuntimeException("No se pudo enviar el correo HTML.");
        }
    }

}
