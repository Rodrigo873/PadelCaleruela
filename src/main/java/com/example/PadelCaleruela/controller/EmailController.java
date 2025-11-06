package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.service.EmailService;
import jakarta.mail.MessagingException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/send")
    public String sendEmail(
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String message
    ) {
        emailService.sendSimpleEmail(to, subject, message);
        return "Correo enviado correctamente a " + to;
    }

    @PostMapping("/send-html")
    public String sendHtml(
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String html
    ) throws MessagingException {
        emailService.sendHtmlEmail(to, subject, html);
        return "Correo HTML enviado correctamente a " + to;
    }
}
