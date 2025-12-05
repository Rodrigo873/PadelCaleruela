package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.*;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final PaymentRepository paymentRepository;
    private final EmailService emailService;
    private final AuthService authService;
    private final PricingService pricingService;
    private final TarifaRepository tarifaRepo;
    private final TarifaFranjaRepository franjaRepo;
    private final UserNotificationService userNotificationService;
    private final NotificationAppService notificationAppService;
    private final NotificationFactory notificationFactory;
    private final PistaRepository pistaRepository;
    @PersistenceContext
    private EntityManager entityManager;

    // 🔹 Crear reserva con duración fija de 1h30min
    @Transactional
    public ReservationDTO createReservation(ReservationDTO dto) {

        User creator = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User current = authService.getCurrentUser();

        // USER solo puede crear su propia reserva
        if (authService.isUser() && !current.getId().equals(dto.getUserId())) {
            throw new AccessDeniedException("No puedes crear reservas para otro usuario.");
        }

        // ADMIN debe estar en el mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(creator);
        }

        // SUPERADMIN no tiene restricciones

        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = start.plusMinutes(90);
        BigDecimal precio = pricingService.calcularPrecio(start, creator.getAyuntamiento());

        Reservation reservation = new Reservation();
        reservation.setUser(creator);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setPaid(false);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPublic(dto.isPublic());
        reservation.setAyuntamiento(creator.getAyuntamiento());
        reservation.setPrecio(precio);

// 🟦 NUEVO: buscar pista por ID del DTO
        if (dto.getPistaId() == null) {
            throw new IllegalArgumentException("Debe seleccionar una pista para reservar.");
        }

        Pista pista = pistaRepository.findById(dto.getPistaId())
                .orElseThrow(() -> new RuntimeException("La pista seleccionada no existe."));

// 🟪 Si trabajas con multi-ayuntamiento, revisa que coincidan
        if (!pista.getAyuntamiento().getId().equals(creator.getAyuntamiento().getId())) {
            throw new RuntimeException("No puedes reservar una pista de otro ayuntamiento.");
        }

        reservation.setPista(pista);  // 🔥 ASIGNACIÓN FINAL


// Jugadores incluye al creador
        Set<User> jugadores = new HashSet<>();
        jugadores.add(creator);
        reservation.setJugadores(jugadores);

        Reservation saved = reservationRepository.save(reservation);


        // Invitaciones
        if (dto.getJugadores() != null && !dto.getJugadores().isEmpty()) {
            List<Long> invitedIds = dto.getJugadores().stream()
                    .map(UserDTO::getId)
                    .filter(id -> id != null && !id.equals(creator.getId()))
                    .toList();

            if (!invitedIds.isEmpty()) {
                List<User> invitados = userRepository.findAllById(invitedIds);

                // Validación multi-ayuntamiento
                for (User invited : invitados) {
                    authService.ensureSameAyuntamiento(invited);
                }

                for (User invited : invitados) {

                    // 🔹 Guardar invitación
                    Invitation inv = new Invitation();
                    inv.setReservation(saved);
                    inv.setSender(creator);
                    inv.setReceiver(invited);
                    inv.setStatus(InvitationStatus.PENDING);
                    inv.setCreatedAt(LocalDateTime.now());
                    invitationRepository.save(inv);

                    // 🔹 Enviar notificación push
                    try {
                        userNotificationService.sendToUser(
                                invited.getId(),
                                creator.getUsername(),
                                NotificationType.MATCH_INVITATION
                        );
                    } catch (Exception e) {
                        // Evitar que un fallo de notificación rompa la creación de la reserva
                        System.err.println("❌ Error enviando notificación a " +
                                invited.getUsername() + ": " + e.getMessage());
                    }
                }
            }
        }


        return toDTO(saved);
    }


    // 🔹 Nuevo método solo para invitar jugadores (sin crear reserva)
    @Transactional
    public String inviteToReservation(Long reservationId, Long senderId, List<Long> invitedIds) {

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User current = authService.getCurrentUser();
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        // Validación ayuntamiento de la reserva
        authService.ensureSameAyuntamiento(reservation.getAyuntamiento());

        // Solo el creador puede invitar
        if (!reservation.getUser().getId().equals(senderId)) {
            throw new AccessDeniedException("Solo el creador puede invitar jugadores.");
        }

        // ADMIN/USER → solo invitar dentro del ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(sender);
        }

        if (reservation.getJugadores().size() >= 4) {
            throw new RuntimeException("La reserva ya está completa.");
        }

        for (Long invitedId : invitedIds) {

            if (invitedId.equals(senderId)) continue;

            User invited = userRepository.findById(invitedId)
                    .orElseThrow(() -> new RuntimeException("Jugador no encontrado: " + invitedId));

            // Validación multi-ayuntamiento
            authService.ensureSameAyuntamiento(invited);

            // Evitar duplicados
            List<Invitation> existing = invitationRepository
                    .findAllByReservationIdAndReceiverIdOrderByCreatedAtAsc(reservationId, invitedId);

            Invitation invToNotify = null;

            if (!existing.isEmpty()) {
                Invitation last = existing.get(existing.size() - 1);

                if (last.getStatus() == InvitationStatus.PENDING) {
                    // Ya hay una invitación pendiente → no hacemos nada
                    continue;
                }

                // Reutilizamos invitación anterior
                last.setStatus(InvitationStatus.PENDING);
                last.setCreatedAt(LocalDateTime.now());
                invitationRepository.save(last);

                invToNotify = last;

            } else {
                // Creamos nueva invitación
                Invitation inv = new Invitation();
                inv.setReservation(reservation);
                inv.setSender(sender);
                inv.setReceiver(invited);
                inv.setStatus(InvitationStatus.PENDING);
                inv.setCreatedAt(LocalDateTime.now());
                invitationRepository.save(inv);

                invToNotify = inv;
            }

            // 🔔 Enviar notificación al invitado
            try {
                userNotificationService.sendToUser(
                        invited.getId(),
                        sender.getUsername(),
                        NotificationType.MATCH_INVITATION
                );
            } catch (Exception e) {
                System.err.println("❌ Error enviando notificación de invitación a "
                        + invited.getUsername() + ": " + e.getMessage());
            }
        }

        return "Invitaciones enviadas correctamente.";
    }



    // Service
    @Transactional(readOnly = true)
    public Long findPublicReservationId(LocalDate date, LocalTime time) {
        LocalDateTime start = LocalDateTime.of(date, time);
        return reservationRepository
                .findFirstByStartTimeAndStatusNotAndIsPublicTrue(start, ReservationStatus.CANCELED)
                .map(Reservation::getId)
                .orElse(null);
    }


    public List<InfoReservationDTO> getAllInfoReservation() {

        User current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) {
            return reservationRepository.findAll()
                    .stream()
                    .map(this::toDTOinfo)
                    .toList();
        }

        if (authService.isAdmin()) {
            Long ayId = current.getAyuntamiento().getId();
            return reservationRepository.findByAyuntamientoId(ayId)
                    .stream()
                    .map(this::toDTOinfo)
                    .toList();
        }

        throw new AccessDeniedException("No tienes permiso para ver esta información.");
    }



    // 🔹 Cancelar reservas no pagadas después de 15 minutos
    @Transactional
    public void cancelUnpaidReservations() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(15);
        List<Reservation> toCancel = reservationRepository.findByPaidFalseAndCreatedAtBefore(limit);

        if (toCancel.isEmpty()) return;

        for (Reservation r : toCancel) {

            // Copia de jugadores antes de limpiar
            List<User> jugadoresOriginales =
                    (r.getJugadores() != null)
                            ? List.copyOf(r.getJugadores())
                            : List.of();

            invitationRepository.deleteAllByReservationId(r.getId());
            r.setStatus(ReservationStatus.CANCELED);

            try {
                User creator = r.getUser();

                // ----------------------------------------------------------
                // 🔥 1) Notificar al creador
                // ----------------------------------------------------------
                if (creator != null) {
                    sendAndSaveNotification(
                            creator,
                            creator,
                            NotificationType.RESERVATION_TIME_CANCELLED,
                            r
                    );
                }

                // ----------------------------------------------------------
                // 🔥 2) Notificar jugadores originales
                // ----------------------------------------------------------
                for (User invited : jugadoresOriginales) {
                    if (invited == null) continue;
                    if (creator != null && invited.getId().equals(creator.getId())) continue;

                    sendAndSaveNotification(
                            invited,
                            creator,
                            NotificationType.RESERVATION_TIME_CANCELLED,
                            r
                    );
                }

            } catch (Exception e) {
                System.out.println("⚠ Error enviando notificación de reserva cancelada: " + e.getMessage());
            }

            // Limpieza final
            if (r.getJugadores() != null) {
                r.getJugadores().clear();
            }
        }

        reservationRepository.saveAll(toCancel);

        entityManager.flush();
        entityManager.clear();

        System.out.println("🔸 Canceladas automáticamente " + toCancel.size() + " reservas impagas.");
    }

    private void sendAndSaveNotification(
            User targetUser,
            User fromUser,
            NotificationType type,
            Reservation reservation
    ) {
        // ---------- 🔹 Mensajes (según tipo) ----------
        String title = notificationFactory.getTitle(type);
        String body = notificationFactory.getMessage(type, fromUser.getUsername());

        // ---------- 🔹 Datos adicionales (JSON) ----------
        String extraJson = """
        {
          "reservationId": %d
        }
        """.formatted(reservation.getId());

        try {
            // ---------- 🔥 1) Enviar push ----------
            userNotificationService.sendToUser(targetUser.getId(), fromUser.getUsername(), type);

        } catch (Exception e) {
            System.out.println("⚠ Error enviando push: " + e.getMessage());
        }

        // ---------- 🔥 2) Guardar en BD ----------
        Notification n = new Notification();
        n.setUserId(targetUser.getId());
        n.setSenderId(fromUser.getId());
        n.setType(type);
        n.setTitle(title);
        n.setMessage(body);
        n.setExtraData(extraJson);

        notificationAppService.saveNotification(n);
    }




    // 🔹 Ejecutar automáticamente cada 1 minutos
    @Scheduled(fixedRate = 60000) // 300000 ms = 5 min 60000 ms=1 min
    public void autoCancelPendingReservations() {
        cancelUnpaidReservations();
    }


    // 🔔 Aviso cuando queden 5 minutos para pagar (a los 10 minutos de creada)
    @Scheduled(fixedRate = 60000) // se ejecuta cada minuto
    @Transactional
    public void notifyReserveAlmostExpired() {

        LocalDateTime now = LocalDateTime.now();

        // Reservas con 10 minutos desde su creación (faltan 5 para cancelar)
        LocalDateTime thresholdStart = now.minusMinutes(11); // para evitar microsegundos raros
        LocalDateTime thresholdEnd = now.minusMinutes(9);

        List<Reservation> pending = reservationRepository
                .findByPaidFalseAndStatusAndCreatedAtBetween(
                        ReservationStatus.PENDING,
                        thresholdStart,
                        thresholdEnd
                );

        if (pending.isEmpty()) return;

        for (Reservation r : pending) {

            User creator = r.getUser();
            if (creator == null) continue;

            // -----------------------------------------------------
            // 🔔 1) Enviar Notificación PUSH al creador
            // -----------------------------------------------------
            try {
                userNotificationService.sendToUser(
                        creator.getId(),
                        "Sistema",
                        NotificationType.PAYMENT_REMINDER
                );
            } catch (Exception e) {
                System.err.println("⚠ Error enviando push (faltan 5 min): " + e.getMessage());
            }

            // -----------------------------------------------------
            // 🔔 2) Guardar notificación en BD (opcional)
            // -----------------------------------------------------
            Notification n = new Notification();
            n.setUserId(creator.getId());
            n.setSenderId(null);  // sistema
            n.setType(NotificationType.PAYMENT_REMINDER);
            n.setTitle("Quedan 5 minutos para pagar tu reserva");
            n.setMessage("Tu reserva está a punto de cancelarse por falta de pago.");
            n.setExtraData("""
            {
              "reservationId": %d
            }
        """.formatted(r.getId()));

            notificationAppService.saveNotification(n);
        }

        System.out.println("🔔 Avisos enviados a " + pending.size() +
                " reservas pendientes (faltan 5 minutos).");
    }


    public List<ReservationDTO> getReservationsForDay(LocalDate date) {

        User current = authService.getCurrentUser();

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Reservation> reservations;

        if (authService.isSuperAdmin()) {
            reservations = reservationRepository.findByStartTimeBetween(startOfDay, endOfDay);
        } else {
            Long ayId = current.getAyuntamiento().getId();
            reservations = reservationRepository.findByStartTimeBetweenAndAyuntamientoId(startOfDay, endOfDay, ayId);
        }

        return reservations.stream()
                .map(this::toDTO)
                .toList();
    }


    // ReservationService
    @Transactional(readOnly = true)
    public List<ReservationWithPistaDTO> getReservationsByUserAndStatus(Long userId, String statusStr) {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver reservas de otros usuarios.");
        }

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target);
        }

        ReservationStatus status;
        try {
            status = ReservationStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de reserva no válido: " + statusStr);
        }

        List<Reservation> creadas =
                reservationRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(userId, status);

        List<Long> idsAceptadas = invitationRepository
                .findByReceiver_IdAndStatus(userId, InvitationStatus.ACCEPTED)
                .stream()
                .map(inv -> inv.getReservation().getId())
                .distinct()
                .toList();

        List<Reservation> comoInvitado = idsAceptadas.isEmpty()
                ? List.of()
                : reservationRepository.findByIdInAndStatusOrderByCreatedAtDesc(idsAceptadas, status);

        List<Reservation> todas = new ArrayList<>();
        todas.addAll(creadas);
        todas.addAll(comoInvitado);

        List<Reservation> sinDuplicados = todas.stream().distinct().toList();

        if (!authService.isSuperAdmin()) {
            sinDuplicados = sinDuplicados.stream()
                    .filter(r ->
                            Objects.equals(
                                    r.getAyuntamiento().getId(),
                                    current.getAyuntamiento().getId()
                            )
                    )
                    .toList();
        }

        // 🔥 Mapeo final actualizado con DTO extendido con pista
        return sinDuplicados.stream()
                .map(res -> {

                    Pista pista = res.getPista();

                    ReservationWithPistaDTO dto = new ReservationWithPistaDTO();
                    dto.setId(res.getId());
                    dto.setUserId(res.getUser().getId());
                    dto.setStartTime(res.getStartTime());
                    dto.setEndTime(res.getEndTime());
                    dto.setCreatedAt(res.getCreatedAt());
                    dto.setEsCreador(res.getUser().getId().equals(userId));
                    dto.setStatus(res.getStatus().name());
                    dto.setPublic(res.isPublic());
                    dto.setPrecio(res.getPrecio());

                    // Jugadores igual que en tu StatustoDTO
                    dto.setJugadores(
                            res.getJugadores().stream()
                                    .map(user -> {
                                        UserDTO u = new UserDTO();
                                        u.setId(user.getId());
                                        u.setUsername(user.getUsername());
                                        u.setFullName(user.getFullName());
                                        u.setEmail(user.getEmail());
                                        return u;
                                    })
                                    .collect(Collectors.toList())
                    );

                    // Mensaje igual que StatustoDTO
                    if (res.getStatus() == ReservationStatus.PENDING) {
                        if (Objects.equals(res.getUser().getId(), userId)) {
                            dto.setMensaje("Pendiente — esperando completar el pago.");
                        } else {
                            dto.setMensaje("El usuario " + res.getUser().getFullName() + " aún no ha pagado.");
                        }
                    } else if (res.getStatus() == ReservationStatus.CONFIRMED) {
                        dto.setMensaje("✅ Reserva confirmada.");
                    } else if (res.getStatus() == ReservationStatus.CANCELED) {
                        dto.setMensaje("❌ Reserva cancelada.");
                    }

                    // Datos de la pista
                    dto.setPistaId(pista.getId());
                    dto.setPistaNombre(pista.getNombre());
                    dto.setPistaTieneCerradura(!pista.getLocks().isEmpty());

                    return dto;

                })
                .toList();
    }





    @Transactional
    public void cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        User current = authService.getCurrentUser();

        // USER → solo cancelar su reserva
        if (authService.isUser() && !reservation.getUser().getId().equals(current.getId())) {
            throw new AccessDeniedException("No puedes cancelar reservas de otros usuarios.");
        }

        // ADMIN → solo si pertenece al ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
        }

        if (!reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permiso para cancelar esta reserva.");
        }

        // Copiar los jugadores antes de limpiar
        List<User> jugadores = new ArrayList<>(reservation.getJugadores());
        User creator = reservation.getUser();

        // Eliminar invitaciones
        invitationRepository.deleteAllByReservationId(reservationId);

        // Eliminar pago si existe
        if (reservation.getPayment() != null) {
            paymentRepository.delete(reservation.getPayment());
        }

        // Cambiar estado
        reservation.setStatus(ReservationStatus.CANCELED);

        // ---------------------------
        // 📩 ENVÍO DE EMAILS (igual)
        // ---------------------------
        LocalDateTime fechaHora = reservation.getStartTime();
        String creador = reservation.getUser().getFullName();
        String fechaFormateada = fechaHora.format(
                DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", new Locale("es", "ES"))
        );

        StringBuilder jugadoresList = new StringBuilder();
        for (User jugador : jugadores) {
            jugadoresList.append("<li>").append(jugador.getFullName()).append("</li>");
        }

        String html = """
    <div style="font-family: Arial, sans-serif; color: #333;">
        <h2 style="color: #d32f2f;">Reserva Cancelada</h2>
        <p>Hola,</p>
        <p>La reserva ha sido <strong>cancelada</strong> por <b>%s</b>.</p>
        <p><strong>Fecha y hora:</strong> %s</p>
        <p><strong>Jugadores de la reserva:</strong></p>
        <ul>%s</ul>
        <p>Si crees que esto fue un error, contacta con el administrador.</p>
        <hr>
        <p style="font-size: 0.9rem; color: #555;">Club de Pádel Caleruela</p>
    </div>
    """.formatted(creador, fechaFormateada, jugadoresList);

        for (User jugador : jugadores) {
            if (jugador.getEmail() != null && !jugador.getEmail().isEmpty()) {
                emailService.sendHtmlEmail(
                        jugador.getEmail(),
                        "Reserva cancelada - " + fechaFormateada,
                        html
                );
            }
        }

        // ---------------------------
        // 🔔 NOTIFICACIONES PUSH + BD
        // ---------------------------
        Set<Long> notificados = new HashSet<>();

        // 1️⃣ Al creador
        sendAndSaveNotification(creator, current, NotificationType.RESERVATION_CANCELLED, reservation);
        notificados.add(creator.getId());

        // 2️⃣ A los jugadores
        for (User jugador : jugadores) {
            if (jugador != null && !notificados.contains(jugador.getId())) {
                sendAndSaveNotification(jugador, current, NotificationType.RESERVATION_CANCELLED, reservation);
                notificados.add(jugador.getId());
            }
        }

        // Eliminar reserva
        reservationRepository.delete(reservation);
    }



    @Transactional
    public void updateReservationStatusToCanceled(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permiso para cancelar esta reserva.");
        }

        List<User> jugadores = new ArrayList<>(reservation.getJugadores());
        User creator = reservation.getUser();

        invitationRepository.deleteAllByReservationId(reservationId);

        if (reservation.getPayment() != null) {
            reservation.getPayment().setStatus(Payment.Status.CANCELED);
            paymentRepository.save(reservation.getPayment());
        }

        reservation.setStatus(ReservationStatus.CANCELED);

        // ---------------------------
        // 📩 EMAILS (sin cambios)
        // ---------------------------
        LocalDateTime fechaHora = reservation.getStartTime();
        String fechaFormateada = fechaHora.format(
                DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", new Locale("es", "ES"))
        );
        String creador = creator.getFullName();

        StringBuilder jugadoresList = new StringBuilder();
        for (User jugador : jugadores) {
            jugadoresList.append("<li>").append(jugador.getFullName()).append("</li>");
        }

        String html = """
    <div style="font-family: Arial, sans-serif; color: #333;">
        <h2 style="color: #d32f2f;">Reserva Cancelada</h2>
        <p>Hola,</p>
        <p>La reserva ha sido <strong>cancelada</strong> por <b>%s</b>.</p>
        <p><strong>Fecha y hora:</strong> %s</p>
        <p><strong>Jugadores de la reserva:</strong></p>
        <ul>%s</ul>
        <hr>
        <p style="font-size: 0.9rem; color: #555;">Club de Pádel Caleruela</p>
    </div>
    """.formatted(creador, fechaFormateada, jugadoresList);

        for (User jugador : jugadores) {
            if (jugador.getEmail() != null && !jugador.getEmail().isEmpty()) {
                emailService.sendHtmlEmail(
                        jugador.getEmail(),
                        "Reserva cancelada - " + fechaFormateada,
                        html
                );
            }
        }

        // ---------------------------
        // 🔔 NOTIFICACIONES PUSH + BD
        // ---------------------------
        Set<Long> notificados = new HashSet<>();

        // 1️⃣ Al creador
        sendAndSaveNotification(creator, creator, NotificationType.RESERVATION_CANCELLED, reservation);
        notificados.add(creator.getId());

        // 2️⃣ A los jugadores
        for (User jugador : jugadores) {
            if (jugador != null && !notificados.contains(jugador.getId())) {
                sendAndSaveNotification(jugador, creator, NotificationType.RESERVATION_CANCELLED, reservation);
                notificados.add(jugador.getId());
            }
        }

        reservationRepository.save(reservation);
    }




    private BigDecimal calcularPrecio(LocalTime hora, Ayuntamiento a) {

        // 1️⃣ Obtener tarifa base del ayuntamiento
        Tarifa tarifa = tarifaRepo.findByAyuntamientoId(a.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "El ayuntamiento no tiene una tarifa configurada"
                ));

        // 2️⃣ Obtener franjas especiales
        List<TarifaFranja> franjas = franjaRepo.findByAyuntamientoId(a.getId());

        int hour = hora.getHour();

        // 3️⃣ Buscar si la hora cae en alguna franja
        for (TarifaFranja fr : franjas) {
            // Ej: 10:00 cae entre 9 y 12
            if (hour >= fr.getHoraInicio() && hour < fr.getHoraFin()) {
                return fr.getPrecio();
            }
        }

        // 4️⃣ Si no está dentro de ninguna franja → precio base
        return tarifa.getPrecioBase();
    }



    // 🔹 Devuelve las horas disponibles de un día
    // 🔹 Genera los slots disponibles de un día
    public List<HourSlotDTO> getAvailableHours(LocalDate date) {

        User current = authService.getCurrentUser();
        Ayuntamiento ay = current.getAyuntamiento();

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Reservation> reservations;
        if (authService.isSuperAdmin()) {
            reservations = reservationRepository
                    .findByStartTimeBetween(startOfDay, endOfDay)
                    .stream()
                    .filter(r -> r.getStatus() != ReservationStatus.CANCELED)
                    .toList();
        } else {
            reservations = reservationRepository
                    .findByStartTimeBetweenAndAyuntamientoId(
                            startOfDay, endOfDay, ay.getId()
                    );
        }

        List<Pista> pistas = pistaRepository.findByAyuntamientoIdAndActivaTrue(ay.getId());
        Long currentUserId = resolveCurrentUserId();

        List<HourSlotDTO> result = new ArrayList<>();

        // 🔥 Generación multi-pista con horario individual
        for (Pista pista : pistas) {

            // ⏰ Horario específico de la pista
            LocalTime opening = pista.getApertura();
            LocalTime closing = pista.getCierre();

            // Generar slots propios
            List<LocalTime> slots = new ArrayList<>();
            // Normalizar medianoche si fuera usada como cierre
            if (closing.equals(LocalTime.MIDNIGHT)) {
                closing = LocalTime.of(23, 59);
            }

            // Si horarios mal configurados, no generar
            if (opening.isAfter(closing)) {
                return new ArrayList<>();
            }

            for (LocalTime t = opening;
                 t.equals(closing) || t.isBefore(closing);
                 t = t.plusMinutes(90))
            {
                slots.add(t);

                // 🚨 Backup extra anti-infinito
                if (t.plusMinutes(90).isBefore(t)) {
                    break;
                }
            }


            for (LocalTime slot : slots) {

                Reservation reservation = reservations.stream()
                        .filter(r -> r.getPista() != null)
                        .filter(r -> r.getPista().getId().equals(pista.getId()))
                        .filter(r -> r.getStartTime().toLocalTime().equals(slot))
                        .filter(r -> r.getStatus() != ReservationStatus.CANCELED)
                        .findFirst()
                        .orElse(null);

                BigDecimal precio = calcularPrecio(slot, ay);

                if (reservation != null) {
                    String status = switch (reservation.getStatus()) {
                        case PENDING -> "PENDING_PAYMENT";
                        case CONFIRMED -> "PAID";
                        default -> "AVAILABLE";
                    };

                    boolean esCreador = currentUserId != null &&
                            reservation.getUser() != null &&
                            reservation.getUser().getId().equals(currentUserId);

                    List<PlayerInfoDTO> players = reservation.getJugadores().stream()
                            .filter(p -> !reservation.isPlayerRejected(p))
                            .map(p -> {
                                boolean accepted = invitationRepository
                                        .findByReservationAndReceiver(reservation, p)
                                        .map(inv -> inv.getStatus() == InvitationStatus.ACCEPTED)
                                        .orElse(true);

                                return new PlayerInfoDTO(
                                        p.getId(),
                                        p.getUsername(),
                                        p.getProfileImageUrl() != null
                                                ? p.getProfileImageUrl()
                                                : "https://ui-avatars.com/api/?name=" + p.getUsername(),
                                        accepted,
                                        p.getStatus()
                                );
                            })
                            .toList();

                    result.add(new HourSlotDTO(
                            slot,
                            status,
                            reservation.isPublic(),
                            players,
                            esCreador,
                            reservation.getId(),
                            precio,
                            pista.getId(),
                            pista.getNombre(),
                            date
                    ));

                } else {
                    // disponible
                    result.add(new HourSlotDTO(
                            slot,
                            "AVAILABLE",
                            false,
                            List.of(),
                            false,
                            null,
                            precio,
                            pista.getId(),
                            pista.getNombre(),
                            date
                    ));
                }
            }
        }

        // ⏱ Expirar slots pasados si es hoy
        if (date.equals(LocalDate.now())) {
            LocalTime now = LocalTime.now();
            result.forEach(slotDto -> {
                if (slotDto.getDate().equals(LocalDate.now()) && slotDto.getTime().isBefore(LocalTime.now())) {
                    slotDto.setStatus("EXPIRED");
                }
            });
        }

        return result;
    }



    private Long resolveCurrentUserId() {
        try {
            var ctx = SecurityContextHolder.getContext();
            if (ctx == null) return null;

            var auth = ctx.getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;

            // Evita el "anonymousUser"
            if ("anonymousUser".equalsIgnoreCase(String.valueOf(auth.getPrincipal()))) {
                return null;
            }

            String key;
            Object principal = auth.getPrincipal();

            if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud) {
                key = ud.getUsername(); // suele ser username o email según tu UserDetailsService
            } else {
                key = auth.getName();   // fallback: lo que ponga el token
            }

            return userRepository.findByUsernameOrEmail(key)
                    .map(User::getId)
                    .orElse(null);

        } catch (Exception e) {
            return null;
        }
    }




    //Reservas de usuario
    public List<ReservationWithPistaDTO> getReservationsByUser(Long userId) {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // USER → solo ver las suyas
        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver reservas de otro usuario.");
        }

        // ADMIN → solo dentro de su ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target);
        }

        List<Reservation> reservations = reservationRepository.findByUserOrderByStartTimeDesc(target);

        return reservations.stream().map(res -> {

            Pista pista = res.getPista();

            ReservationWithPistaDTO dto = new ReservationWithPistaDTO();
            dto.setId(res.getId());
            dto.setUserId(res.getUser().getId());
            dto.setStartTime(res.getStartTime());
            dto.setEndTime(res.getEndTime());
            dto.setCreatedAt(res.getCreatedAt());
            dto.setEsCreador(res.getUser().getId().equals(current.getId()));
            dto.setStatus(res.getStatus().name());
            dto.setPublic(res.isPublic());
            dto.setPrecio(res.getPrecio());

            // Jugadores igual que en tu StatustoDTO
            dto.setJugadores(
                    res.getJugadores().stream()
                            .map(user -> {
                                UserDTO u = new UserDTO();
                                u.setId(user.getId());
                                u.setUsername(user.getUsername());
                                u.setFullName(user.getFullName());
                                u.setEmail(user.getEmail());
                                return u;
                            })
                            .collect(Collectors.toList())
            );

            // Mensaje igual que en tu StatustoDTO
            if (res.getStatus() == ReservationStatus.PENDING) {
                if (Objects.equals(res.getUser().getId(), current.getId())) {
                    dto.setMensaje("Pendiente — esperando completar el pago.");
                } else {
                    dto.setMensaje("El usuario " + res.getUser().getFullName() + " aún no ha pagado.");
                }
            } else if (res.getStatus() == ReservationStatus.CONFIRMED) {
                dto.setMensaje("✅ Reserva confirmada.");
            } else if (res.getStatus() == ReservationStatus.CANCELED) {
                dto.setMensaje("❌ Reserva cancelada.");
            }

            // Datos de la pista
            dto.setPistaId(pista.getId());
            dto.setPistaNombre(pista.getNombre());
            dto.setPistaTieneCerradura(!pista.getLocks().isEmpty());

            return dto;

        }).toList();
    }


    @Transactional(readOnly = true)
    public List<ReservationDTO> getPendingReservations(Long userId) {
        User current = authService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // USER → solo las suyas
        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver reservas pendientes de otro usuario.");
        }

        // ADMIN → solo dentro de su ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target);
        }

        // 🔹 Obtener las reservas del usuario con estado PENDING
        List<Reservation> pendientes = reservationRepository.findByUser_IdAndStatus(userId, ReservationStatus.PENDING);

        /*🔹 También incluir reservas donde fue invitado y aún no aceptó (opcional)
        List<Long> invitacionesPendientes = invitationRepository
                .findByReceiver_IdAndStatus(userId, InvitationStatus.PENDING)
                .stream()
                .map(inv -> inv.getReservation().getId())
                .distinct()
                .toList();

        List<Reservation> comoInvitado = invitacionesPendientes.isEmpty()
                ? List.of()
                : reservationRepository.findByIdInAndStatus(invitacionesPendientes, ReservationStatus.PENDING);

         */
        // 🔹 Unir ambas listas sin duplicar
        List<Reservation> todas = new ArrayList<>();
        todas.addAll(pendientes);
        //todas.addAll(comoInvitado);

        List<Reservation> sinDuplicados = todas.stream()
                .distinct()
                .sorted(Comparator.comparing(Reservation::getCreatedAt).reversed())
                .toList();

        // 🔹 Mapear a DTOs
        return sinDuplicados.stream()
                .map(res -> StatustoDTO(res, userId))
                .toList();
    }



    private ReservationDTO toDTO(Reservation reservation) {
        ReservationDTO dto = new ReservationDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setStartTime(reservation.getStartTime());
        dto.setEndTime(reservation.getEndTime());
        dto.setCreatedAt(reservation.getCreatedAt());
        dto.setPublic(reservation.isPublic());

        // Mapea jugadores a UserDTO básicos
        dto.setJugadores(
                reservation.getJugadores().stream()
                        .map(user -> {
                            UserDTO u = new UserDTO();
                            u.setId(user.getId());
                            u.setUsername(user.getUsername());
                            u.setFullName(user.getFullName());
                            u.setEmail(user.getEmail());
                            return u;
                        })
                        .collect(Collectors.toList())
        );
        return dto;
    }


    private InfoReservationDTO toDTOinfo(Reservation reservation) {
        InfoReservationDTO dto = new InfoReservationDTO();
        dto.setId(reservation.getId());
        dto.setStartTime(reservation.getStartTime());
        dto.setStatus(String.valueOf(reservation.getStatus()));
        return dto;
    }
    private ReservationDTO StatustoDTO(Reservation reservation, Long currentUserId) {
        ReservationDTO dto = new ReservationDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setStartTime(reservation.getStartTime());
        dto.setEndTime(reservation.getEndTime());
        dto.setCreatedAt(reservation.getCreatedAt());
        dto.setEsCreador(reservation.getUser().getId().equals(currentUserId));
        dto.setStatus(reservation.getStatus().name()); // 👈 aquí

        // 🔹 Mapea jugadores
        dto.setJugadores(
                reservation.getJugadores().stream()
                        .map(user -> {
                            UserDTO u = new UserDTO();
                            u.setId(user.getId());
                            u.setUsername(user.getUsername());
                            u.setFullName(user.getFullName());
                            u.setEmail(user.getEmail());
                            return u;
                        })
                        .collect(Collectors.toList())
        );

        // 🔹 Mensaje contextual según usuario y estado
        if (reservation.getStatus() == ReservationStatus.PENDING) {
            if (Objects.equals(reservation.getUser().getId(), currentUserId)) {
                dto.setMensaje("Pendiente — esperando completar el pago.");
            } else {
                dto.setMensaje("El usuario " + reservation.getUser().getFullName() + " aún no ha pagado.");
            }
        } else if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            dto.setMensaje("✅ Reserva confirmada.");
        } else if (reservation.getStatus() == ReservationStatus.CANCELED) {
            dto.setMensaje("❌ Reserva cancelada.");
        }

        return dto;
    }


    /** 🔹 Devuelve los jugadores de una reserva con su estado */
    public List<ReservationPlayerDTO> getJugadoresPorReserva(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        User current = authService.getCurrentUser();

        // Superadmin → full
        if (!authService.isSuperAdmin()) {

            // Admin → mismo ayuntamiento
            if (authService.isAdmin()) {
                authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
            }

            // User → solo si participa o si fue invitado o si es pública
            boolean puedeVer =
                    reservation.isPublic() ||
                            reservation.getUser().getId().equals(current.getId()) ||
                            reservation.getJugadores().stream().anyMatch(j -> j.getId().equals(current.getId())) ||
                            invitationRepository.existsByReservationIdAndReceiverId(reservationId, current.getId());

            if (!puedeVer) {
                throw new AccessDeniedException("No tienes permiso para ver los jugadores de esta reserva.");
            }
        }

        List<ReservationPlayerDTO> jugadores = new ArrayList<>();

        // ✅ Añadir el creador de la reserva (siempre aceptado)
        jugadores.add(new ReservationPlayerDTO(
                reservation.getUser().getId(),
                reservation.getUser().getUsername(),
                reservation.getUser().getFullName(),
                "ACEPTADA"
        ));

        // 🔍 Buscar invitaciones asociadas a la reserva
        List<Invitation> invitaciones = invitationRepository.findByReservationId(reservationId);

        for (Invitation inv : invitaciones) {
            String estado = switch (inv.getStatus()) {
                case ACCEPTED -> "ACEPTADA";
                case REJECTED -> "CANCELADA";
                default -> "PENDIENTE";
            };

            jugadores.add(new ReservationPlayerDTO(
                    inv.getReceiver().getId(),
                    inv.getReceiver().getUsername(),
                    inv.getReceiver().getFullName(),
                    estado
            ));
        }

        return jugadores;
    }

    /**
     * 🔹 Devuelve todas las reservas confirmadas de un usuario
     */
    public List<ReservationDTO> getConfirmedReservationsByUser(Long userId) {
        List<Reservation> reservations = reservationRepository.findConfirmedByUserId(userId);
        return reservations.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public int getConfirmedReservationsCount(Long userId) {
        return reservationRepository.countConfirmedByUserId(userId);
    }

    @Transactional
    public String joinPublicReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        // Validación multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
        }

        if (!reservation.isPublic()) {
            throw new RuntimeException("Esta reserva no es pública.");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new RuntimeException("No puedes unirte a una reserva que no esté pendiente o confirmada.");
        }

        if (reservation.getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("No puedes unirte a una reserva que ya comenzó o finalizó.");
        }

        if (reservation.getJugadores().size() >= 4) {
            throw new RuntimeException("La reserva ya está completa (máximo 4 jugadores).");
        }

        // Evitar que el creador se una dos veces
        if (reservation.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Ya eres el creador de esta reserva.");
        }

        // Evitar duplicados
        boolean yaUnido = reservation.getJugadores().stream()
                .anyMatch(u -> u.getId().equals(user.getId()));

        if (yaUnido) {
            throw new RuntimeException("Ya estás unido a esta reserva.");
        }

        // ✅ Añadir jugador y guardar
        reservation.getJugadores().add(user);
        reservationRepository.save(reservation);

        return "✅ Te has unido correctamente a la reserva pública.";
    }

    @Transactional
    public String leavePublicReservation(Long reservationId, String principalName) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        User user = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + principalName));

        // Multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
        }

        boolean estaEnReserva = reservation.getJugadores().stream()
                .anyMatch(u -> u.getId().equals(user.getId()));

        if (!estaEnReserva) {
            throw new RuntimeException("No estás unido a esta reserva.");
        }

        if (reservation.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("El creador no puede abandonar la partida. Solo cancelarla.");
        }

        // ❌ Eliminar jugador de la reserva
        reservation.getJugadores().removeIf(u -> u.getId().equals(user.getId()));
        reservationRepository.save(reservation);

        // 🔁 Marcar invitaciones previas como REJECTED
        List<Invitation> invitaciones = invitationRepository
                .findAllByReservationAndReceiver(reservation, user);

        if (!invitaciones.isEmpty()) {
            invitaciones.forEach(inv -> {
                inv.setStatus(InvitationStatus.REJECTED);
                invitationRepository.save(inv);
            });
        }

        // -------------------------------------------------------------------
        // 🔔 NOTIFICACIONES (PUSH + GUARDAR EN BASE DE DATOS)
        // -------------------------------------------------------------------
        try {
            for (User jugador : reservation.getJugadores()) {

                // No notificar al que abandona
                if (jugador.getId().equals(user.getId())) continue;

                sendAndSaveNotification(
                        jugador,                     // receptor
                        user,                        // quien abandona
                        NotificationType.MATCH_PLAYER_LEFT,
                        reservation                  // extraData: reservationId
                );
            }

        } catch (Exception e) {
            System.out.println("⚠ Error enviando notificaciones (abandonar reserva): " + e.getMessage());
        }

        return "Has abandonado la partida correctamente.";
    }

    @Transactional
    public String kickPlayerFromReservation(Long reservationId, Long kickedUserId, String principalName) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        User creator = userRepository.findByUsernameOrEmail(principalName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + principalName));

        User kicked = userRepository.findById(kickedUserId)
                .orElseThrow(() -> new RuntimeException("Jugador a expulsar no encontrado."));

        // 🔐 Validar multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
        }

        // 🔒 Solo el creador puede expulsar
        if (!reservation.getUser().getId().equals(creator.getId())) {
            throw new RuntimeException("Solo el creador de la reserva puede expulsar jugadores.");
        }

        // ❌ No permitir que el creador se expulse a sí mismo
        if (creator.getId().equals(kicked.getId())) {
            throw new RuntimeException("No puedes expulsarte a ti mismo. Debes cancelar la reserva.");
        }

        // ❌ El jugador debe estar en la reserva
        boolean estaEnReserva = reservation.getJugadores().stream()
                .anyMatch(u -> u.getId().equals(kicked.getId()));

        if (!estaEnReserva) {
            throw new RuntimeException("Este jugador no está en la partida.");
        }

        // 🔥 Expulsar al jugador
        reservation.getJugadores()
                .removeIf(u -> u.getId().equals(kicked.getId()));
        reservationRepository.save(reservation);

        // 🔁 Marcar invitaciones previas como REJECTED si existían
        List<Invitation> invitaciones = invitationRepository
                .findAllByReservationAndReceiver(reservation, kicked);

        if (!invitaciones.isEmpty()) {
            invitaciones.forEach(inv -> {
                inv.setStatus(InvitationStatus.REJECTED);
                invitationRepository.save(inv);
            });
        }

        // -------------------------------------------------------------------
        // 🔔 Notificación para el expulsado
        // -------------------------------------------------------------------
        try {
            sendAndSaveNotification(
                    kicked,                         // receptor: expulsado
                    creator,                        // actor: el creador
                    NotificationType.MATCH_PLAYER_KICKED,
                    reservation                     // extraData: reservationId
            );

        } catch (Exception e) {
            System.out.println("⚠ Error enviando push al expulsado: " + e.getMessage());
        }

        // -------------------------------------------------------------------
        // 🔔 Notificar al resto de jugadores (si quieres mantener coherencia)
        // -------------------------------------------------------------------
        try {
            for (User jugador : reservation.getJugadores()) {

                if (jugador.getId().equals(creator.getId())) continue; // no notificar al creador

                sendAndSaveNotification(
                        jugador,
                        creator,
                        NotificationType.MATCH_PLAYER_KICKED,
                        reservation
                );
            }

        } catch (Exception e) {
            System.out.println("⚠ Error enviando notificaciones a jugadores: " + e.getMessage());
        }

        return kicked.getUsername() + " ha sido expulsado de la partida.";
    }




    public boolean usuarioPuedeAbrir(Long reservaId) {

        Reservation reserva = reservationRepository.findById(reservaId)
                .orElse(null);

        if (reserva == null) return false;

        // Ejemplo de reglas reales:
        LocalDateTime now = LocalDateTime.now();

        boolean dentroDeHorario =
                now.isAfter(reserva.getStartTime().minusMinutes(15)) &&
                        now.isBefore(reserva.getEndTime().plusMinutes(5));

        boolean pagada = reserva.isPaid();

        return pagada && dentroDeHorario;
    }

    public Optional<Reservation> findById(Long id) {
        return reservationRepository.findById(id);
    }

    public boolean usuarioPuedeConsultar(Long userId, Reservation reserva) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String rol = user.getRole().name(); // SUPERADMIN, ADMIN, USER

        System.out.println("🔎 Validando permisos del usuario " + userId + " rol=" + rol);

        // SUPERADMIN y ADMIN SIEMPRE PUEDEN
        if (rol.equals("SUPERADMIN") || rol.equals("ADMIN")) {
            return true;
        }

        // CREATOR de la reserva
        if (reserva.getUser().getId().equals(userId)) {
            return true;
        }

        // JUGADOR dentro de la reserva
        boolean esJugador = reserva.getJugadores().stream()
                .anyMatch(j -> j.getId().equals(userId));

        return esJugador;
    }

    @Transactional(readOnly = true)
    public List<ReservationSummaryDTO> getLast10ConfirmedReservationsForAyuntamiento() {

        User current = authService.getCurrentUser();

        if (!authService.isAdmin() && !authService.isSuperAdmin()) {
            throw new AccessDeniedException("No tienes permiso para ver esta información.");
        }

        Long ayId = current.getAyuntamiento().getId();
        LocalDateTime now = LocalDateTime.now();

        List<Reservation> reservas =
                reservationRepository.findLastConfirmedBeforeNow(
                        now,
                        ayId,
                        org.springframework.data.domain.PageRequest.of(0, 10)
                );

        return reservas.stream()
                .map(this::toSummaryDTO)
                .toList();
    }

    private ReservationSummaryDTO toSummaryDTO(Reservation r) {
        ReservationSummaryDTO dto = new ReservationSummaryDTO();

        dto.setReservationId(r.getId());
        dto.setPistaId(r.getPista().getId());
        dto.setPistaNombre(r.getPista().getNombre());
        dto.setStartTime(r.getStartTime());
        dto.setEndTime(r.getEndTime());

        List<PlayerSimpleDTO> jugadores =
                r.getJugadores().stream()
                        .map(u -> {
                            PlayerSimpleDTO pj =
                                    new PlayerSimpleDTO();
                            pj.setId(u.getId());
                            pj.setUsername(u.getUsername());
                            pj.setProfileImageUrl(u.getProfileImageUrl());
                            return pj;
                        })
                        .toList();

        dto.setJugadores(jugadores);
        return dto;
    }


    public boolean usuarioTieneReservaEnPistaHoy(Long userId, Long pistaId) {
        LocalDate today = LocalDate.now();

        List<Reservation> reservas = reservationRepository.findByPistaIdAndDate(pistaId, today);

        return reservas.stream().anyMatch(r ->
                r.getUser().getId().equals(userId) ||
                        r.getJugadores().stream().anyMatch(j -> j.getId().equals(userId))
        );
    }

    @Transactional(readOnly = true)
    public PistaDTO getPistaVisibleParaUsuario(Long reservaId, Long userId) {

        Reservation reserva = reservationRepository.findById(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String rol = user.getRole().name();

        if (!rol.equals("SUPERADMIN") && !rol.equals("ADMIN")) {
            boolean esCreador = reserva.getUser().getId().equals(userId);
            boolean esJugador = reserva.getJugadores().stream()
                    .anyMatch(j -> j.getId().equals(userId));

            if (!esCreador && !esJugador) {
                throw new RuntimeException("No tienes permiso para ver esta pista");
            }
        }

        Pista pista = reserva.getPista();

        // 🔥 Esto evita el bucle infinito
        return new PistaDTO(pista.getId(),pista.getAyuntamiento().getId() ,pista.getNombre(),pista.isActiva(),pista.getApertura().toString(),pista.getCierre().toString());
    }



    @Transactional(readOnly = true)
    public List<ReservationDTO> getAvailablePublicReservations(Long currentUserId) {

        User current = authService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        List<Reservation> reservations;

        if (authService.isSuperAdmin()) {
            reservations = reservationRepository.findPublicAvailableReservations(now);
        } else {
            reservations = reservationRepository.findPublicAvailableReservationsByAyuntamiento(
                    now,
                    current.getAyuntamiento().getId()
            );
        }

        List<Reservation> publicReservations = reservationRepository.findPublicAvailableReservations(now);

        return publicReservations.stream().map(res -> {
            ReservationDTO dto = new ReservationDTO();
            dto.setId(res.getId());
            dto.setUserId(res.getUser().getId());
            dto.setStartTime(res.getStartTime());
            dto.setEndTime(res.getEndTime());
            dto.setCreatedAt(res.getCreatedAt());
            dto.setStatus(res.getStatus().name());
            dto.setPublic(res.isPublic());
            dto.setEsCreador(res.getUser().getId().equals(currentUserId));

            dto.setJugadores(
                    res.getJugadores().stream()
                            .filter(j -> !res.isPlayerRejected(j))
                            .map(j -> {
                                UserDTO userDTO = new UserDTO();
                                userDTO.setId(j.getId());
                                userDTO.setUsername(j.getUsername());
                                userDTO.setFullName(j.getFullName());
                                userDTO.setProfileImageUrl(j.getProfileImageUrl());
                                return userDTO;
                            })
                            .toList()
            );


            return dto;
        }).toList();
    }

}
