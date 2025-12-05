package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.WelockClient;
import com.example.PadelCaleruela.dto.PaymentDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import com.stripe.model.*;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final WelockClient welockClient;
    private final LockRepository lockRepository;
    private final LockPasswordRepository lockPasswordRepository;
    @Value("${stripe.webhook.secret}")
    private String endPointSecret;
    /**
     * ⚠️ Este endpoint NO debe requerir autenticación de usuario.
     * SOLO valida:
     *   - Firma de Stripe
     *   - Cuenta Connect (stripeAccount)
     */
    public void handleStripeEvent(String payload, String signature) {

        String endpointSecret = endPointSecret;
        if (endpointSecret == null) {
            throw new IllegalStateException("⚠️ STRIPE_WEBHOOK_SECRET no configurado.");
        }

        Event event;

        try {
            event = Webhook.constructEvent(payload, signature, endpointSecret);
        } catch (Exception e) {
            throw new IllegalArgumentException("❌ Firma Stripe NO válida: " + e.getMessage());
        }

        // 🟦 Multi-tenant: Stripe indica qué cuenta Connect originó el evento
        String stripeAccount = event.getAccount();
        if (stripeAccount == null) {
            System.err.println("⚠️ Webhook recibido sin cuenta Stripe asociada. SE IGNORA.");
            return;
        }

        System.out.println("📩 Webhook " + event.getType() + " desde cuenta: " + stripeAccount);
        System.out.println("🧩 EVENTO RECIBIDO: " + event.getType());

        switch (event.getType()) {

            case "payment_intent.succeeded" -> {
                var dataObject = event.getData().getObject();

                // Deserialización FIJA que funciona siempre
                PaymentIntent pi = ApiResource.GSON.fromJson(
                        dataObject.toJson(),
                        PaymentIntent.class
                );

                System.out.println("🟣 PaymentIntent deserializado CORRECTAMENTE: " + pi.getId());
                safeOnPaymentSucceeded(pi, stripeAccount);
            }


            case "payment_intent.payment_failed" -> {
                PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);

                if (pi != null) safeOnPaymentFailed(pi, stripeAccount);
            }

            default -> System.out.println("ℹ️ Evento ignorado por no estar implementado: " + event.getType());
        }
    }

    // =====================================================================================
    // 🟩 PAGO EXITOSO
    // =====================================================================================
    @Transactional
    protected void safeOnPaymentSucceeded(PaymentIntent pi, String stripeAccount) {

        String paymentIdStr = getMeta(pi, "paymentId");
        if (paymentIdStr == null) {
            System.err.println("❌ Webhook sin paymentId. SE IGNORA.");
            return;
        }
        System.out.println("🔍 Metadata recibida:");
        System.out.println(" paymentId=" + getMeta(pi, "paymentId"));
        System.out.println(" reservationId=" + getMeta(pi, "reservationId"));

        Payment p = paymentRepository.findById(Long.valueOf(paymentIdStr)).orElse(null);
        if (p == null) {
            System.err.println("❌ Pago no encontrado en BD. SE IGNORA.");
            return;
        }

        // evitar reprocesar duplicados
        if (p.getStatus() == Payment.Status.SUCCEEDED) {
            System.out.println("ℹ️ Webhook duplicado, pago ya procesado.");
            return;
        }

        Reservation r = p.getReservation();
        if (r == null) {
            System.err.println("❌ No existe reserva asociada. SE IGNORA.");
            return;
        }

        // =========================================================
        // 🔎 Recuperar PaymentMethod desde la cuenta Connect correcta
        // =========================================================
        try {
            if (pi.getPaymentMethod() != null) {
                RequestOptions opts = RequestOptions.builder()
                        .setStripeAccount(stripeAccount)
                        .build();

                PaymentMethod pm = PaymentMethod.retrieve(pi.getPaymentMethod(), opts);

                p.setPaymentMethodId(pm.getId());

                if (pm.getCard() != null) {
                    p.setCardBrand(pm.getCard().getBrand());
                    p.setCardLast4(pm.getCard().getLast4());
                }
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Error recuperando PaymentMethod: " + ex.getMessage());
        }

        // =========================================================
        // 🔎 Recuperar receipt_url (solo desde cuenta correcta)
        // =========================================================
        try {
            RequestOptions opts = RequestOptions.builder()
                    .setStripeAccount(stripeAccount)
                    .build();

            ChargeCollection charges = Charge.list(
                    Map.of("payment_intent", pi.getId()),
                    opts
            );

            if (charges != null && !charges.getData().isEmpty()) {
                Charge ch = charges.getData().get(0);
                p.setProviderReceiptUrl(ch.getReceiptUrl());
            }

        } catch (Exception ex) {
            System.err.println("⚠️ Error recuperando charge: " + ex.getMessage());
        }

        // =========================================================
        // 🟩 Marcar pago/reserva
        // =========================================================
        p.setStatus(Payment.Status.SUCCEEDED);

        r.setPaid(true);
        r.setStatus(ReservationStatus.CONFIRMED);

        paymentRepository.save(p);
        reservationRepository.save(r);

        // =========================================================
        // 🔐 Generar código Welock (ejemplo basado en hora de la reserva)
        // =========================================================
        try {
            // 1️⃣ Calcular apertura válida (5 min antes → redondeo abajo)
            LocalDateTime startRaw = r.getStartTime().minusMinutes(5);
            LocalDateTime start = roundWelockStart(startRaw);

            // 2️⃣ Calcular cierre válido (5 min después → redondeo arriba)
            LocalDateTime endRaw = r.getEndTime().plusMinutes(5);
            LocalDateTime end = roundWelockEnd(endRaw);

            // 3️⃣ Formato EXACTO para Welock: yyyy-MM-dd HH:mm
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            String startTimestamp = start.format(fmt);
            String endTimestamp = end.format(fmt);

            System.out.println("🔧 WeLock timestamps:");
            System.out.println("START: " + startTimestamp);
            System.out.println("END:   " + endTimestamp);

            // 4️⃣ Recuperar cerraduras de la pista
            List<Lock> locks = lockRepository.findLocksByPistaId(r.getPista().getId());

            for (Lock lock : locks) {

                // 5️⃣ Enviar la contraseña a Welock
                String password = welockClient.generateTempPassword(
                        lock.getDeviceNumber(),
                        lock.getBleName(),
                        startTimestamp,
                        endTimestamp,
                        0
                );

                System.out.println("🔐 Código Welock para lock " + lock.getDeviceNumber() + ": " + password);

                // 6️⃣ Guardar la contraseña en la BD
                LockPassword lp = new LockPassword();
                lp.setLock(lock);
                lp.setReservation(r);
                lp.setPassword(password);
                lp.setStartTime(start);
                lp.setEndTime(end);

                lockPasswordRepository.save(lp);
            }
            // =========================================================
            // 📩 Enviar emails con los códigos Welock
            // =========================================================
            try {
                sendConfirmationEmails(r);
            } catch (Exception ex) {
                System.err.println("⚠️ Error enviando email de confirmación: " + ex.getMessage());
            }


        } catch (Exception ex) {
            System.err.println("⚠️ Error generando código Welock: " + ex.getMessage());
        }



        System.out.println("✅ Pago procesado correctamente en multi-tenant.");
    }


    // =====================================================================================
    // ❌ PAGO FALLIDO
    // =====================================================================================
    @Transactional
    protected void safeOnPaymentFailed(PaymentIntent pi, String stripeAccount) {

        String paymentIdStr = getMeta(pi, "paymentId");
        if (paymentIdStr == null) return;

        Payment p = paymentRepository.findById(Long.valueOf(paymentIdStr)).orElse(null);
        if (p == null) return;

        p.setStatus(Payment.Status.FAILED);
        paymentRepository.save(p);

        System.out.println("❌ Pago fallido procesado via webhook.");
    }


    // =====================================================================================
    // No pasa por webhook (esto es para alguien que ponga precio 0 en sus pistas)
    // =====================================================================================
    @Transactional
    public PaymentDTO handleFreeReservation(Payment p) {

        Reservation r = p.getReservation();

        p.setStatus(Payment.Status.SUCCEEDED);
        r.setPaid(true);
        r.setStatus(ReservationStatus.CONFIRMED);

        paymentRepository.save(p);
        reservationRepository.save(r);

        // 🔐 Generar códigos Welock igual que en el webhook
        try {
            LocalDateTime startRaw = r.getStartTime().minusMinutes(5);
            LocalDateTime start = roundWelockStart(startRaw);

            LocalDateTime endRaw = r.getEndTime().plusMinutes(5);
            LocalDateTime end = roundWelockEnd(endRaw);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            String startTs = start.format(fmt);
            String endTs = end.format(fmt);

            List<Lock> locks = lockRepository.findLocksByPistaId(r.getPista().getId());

            for (Lock lock : locks) {
                String password = welockClient.generateTempPassword(
                        lock.getDeviceNumber(),
                        lock.getBleName(),
                        startTs, endTs,
                        0
                );

                LockPassword lp = new LockPassword();
                lp.setLock(lock);
                lp.setReservation(r);
                lp.setPassword(password);
                lp.setStartTime(start);
                lp.setEndTime(end);

                lockPasswordRepository.save(lp);
            }

            sendConfirmationEmails(r);

        } catch (Exception ex) {
            System.err.println("⚠️ Error en Welock para reserva gratuita: " + ex.getMessage());
        }

        PaymentDTO dto = toDTO(p);
        dto.setClientSecret(null); // no hay Stripe
        return dto;
    }
    private PaymentDTO toDTO(Payment p) {
        PaymentDTO dto = new PaymentDTO();
        dto.setId(p.getId());
        dto.setReservationId(p.getReservation().getId());
        dto.setUserId(p.getUser().getId());
        dto.setProvider(p.getProvider());
        dto.setStatus(p.getStatus());
        dto.setAmount(p.getAmount());
        dto.setCurrency(p.getCurrency());
        dto.setPaymentReference(p.getPaymentReference());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setPaymentIntentId(p.getPaymentIntentId());
        dto.setProviderAccountId(p.getProviderAccountId());
        dto.setStripeAccountId(p.getProviderAccountId());
        return dto;
    }

    // =====================================================================================
    // 📩 EMAILS
    // =====================================================================================
    private void sendConfirmationEmails(Reservation r) throws MessagingException {

        Set<User> jugadores = r.getJugadores();
        String creador = r.getUser().getFullName();
        LocalDateTime fechaHora = r.getStartTime();

        // 🗓 Fecha bonita en español
        String fechaFormateada = fechaHora.format(
                DateTimeFormatter.ofPattern(
                        "EEEE d 'de' MMMM 'a las' HH:mm",
                        new Locale("es", "ES")
                )
        );

        // 👥 Lista de jugadores en HTML
        StringBuilder jugadoresList = new StringBuilder();
        jugadores.forEach(j ->
                jugadoresList.append("<li>").append(j.getFullName()).append("</li>")
        );

        // 🔐 Recuperar los códigos Welock asociados a esta reserva
        List<LockPassword> passwords = lockPasswordRepository.findByReservationId(r.getId());

        StringBuilder codesHtml = new StringBuilder();
        for (LockPassword lp : passwords) {
            codesHtml.append("""
            <li>
                <b>%s</b><br/>
                Código: <b>%s</b><br/>
                Desde: %s<br/>
                Hasta: %s
            </li><br/>
        """.formatted(
                    "Cerradura "+lp.getLock().getName(),
                    lp.getPassword(),
                    lp.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    lp.getEndTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            ));
        }

        // 📨 Plantilla HTML
        String html = """
        <div style="font-family: Arial; color: #333;">
            <h2 style="color:#0b5ed7;">🎾 Reserva confirmada</h2>
            <p><b>%s</b> ha realizado y pagado la reserva correctamente.</p>

            <p>
                <b>Fecha:</b> %s<br/>
                <b>Jugadores:</b>
            </p>

            <ul>
                %s
            </ul>

            <h3 style="color:#0b5ed7;">🔐 Códigos de acceso</h3>
            <p>A continuación encontrarás los códigos válidos para las cerraduras correspondientes:</p>

            <ul>
                %s
            </ul>

            <p>Recuerda que los códigos solo funcionan dentro del horario permitido.</p>
        </div>
    """.formatted(
                creador,
                fechaFormateada,
                jugadoresList,
                codesHtml
        );

        // 📩 Enviar email a cada jugador
        for (User jugador : jugadores) {
            if (jugador.getEmail() != null && !jugador.getEmail().isEmpty()) {
                emailService.sendHtmlEmail(
                        jugador.getEmail(),
                        "🎾 Reserva confirmada - " + fechaFormateada,
                        html
                );
            }
        }
    }


    // =====================================================================================
    // 🔧 Helpers
    // =====================================================================================
    private String getMeta(PaymentIntent pi, String key) {
        return pi.getMetadata() != null ? pi.getMetadata().get(key) : null;
    }

    private LocalDateTime roundWelockStart(LocalDateTime t) {
        // Queremos 5 minutos antes
        LocalDateTime target = t.minusMinutes(5);

        int minute = target.getMinute();
        int mod = minute % 15;

        // Si ya cae en un cuarto de hora → perfecto
        if (mod == 0) {
            return target.withSecond(0).withNano(0);
        }

        // Minuto anterior permitido
        int down = minute - mod;

        // Minuto siguiente permitido
        int up = down + 15;

        // Evaluamos cuál está más cerca de "t - 5"
        int distDown = Math.abs(minute - down);
        int distUp = Math.abs(up - minute);

        int chosen = (distDown <= distUp) ? down : up;

        // Si se pasa de 60 → subimos hora
        if (chosen >= 60) {
            return target.plusHours(1)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
        }

        return target.withMinute(chosen).withSecond(0).withNano(0);
    }


    private LocalDateTime roundWelockEnd(LocalDateTime t) {
        // Queremos 5 minutos después
        LocalDateTime target = t.plusMinutes(5);

        int minute = target.getMinute();
        int mod = minute % 15;

        if (mod == 0) {
            return target.withSecond(0).withNano(0);
        }

        int down = minute - mod;
        int up = down + 15;

        int distDown = Math.abs(minute - down);
        int distUp = Math.abs(up - minute);

        int chosen = (distDown <= distUp) ? down : up;

        if (chosen >= 60) {
            return target.plusHours(1)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
        }

        return target.withMinute(chosen).withSecond(0).withNano(0);
    }


}
