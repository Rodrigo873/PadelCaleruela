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
        reservation.setAyuntamiento(creator.getAyuntamiento()); // 🔥 MULTI-AYTO
        reservation.setPrecio(precio);
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
                    Invitation inv = new Invitation();
                    inv.setReservation(saved);
                    inv.setSender(creator);
                    inv.setReceiver(invited);
                    inv.setStatus(InvitationStatus.PENDING);
                    inv.setCreatedAt(LocalDateTime.now());
                    invitationRepository.save(inv);
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

        // Validación ayuntamiento
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
            List<Invitation> existing = invitationRepository.findAllByReservationIdAndReceiverIdOrderByCreatedAtAsc(reservationId, invitedId);

            if (!existing.isEmpty()) {
                Invitation last = existing.get(existing.size() - 1);

                if (last.getStatus() == InvitationStatus.PENDING) continue;

                last.setStatus(InvitationStatus.PENDING);
                last.setCreatedAt(LocalDateTime.now());
                invitationRepository.save(last);

            } else {
                Invitation inv = new Invitation();
                inv.setReservation(reservation);
                inv.setSender(sender);
                inv.setReceiver(invited);
                inv.setStatus(InvitationStatus.PENDING);
                inv.setCreatedAt(LocalDateTime.now());
                invitationRepository.save(inv);
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
            invitationRepository.deleteAllByReservationId(r.getId());
            if (r.getJugadores() != null) r.getJugadores().clear();
            r.setStatus(ReservationStatus.CANCELED);
        }

        reservationRepository.saveAll(toCancel);
        entityManager.flush();
        entityManager.clear(); // 👈 evita que getAvailableHours vea cache viejo

        System.out.println("🔸 Canceladas automáticamente " + toCancel.size() + " reservas impagas.");
    }



    // 🔹 Ejecutar automáticamente cada 1 minutos
    @Scheduled(fixedRate = 60000) // 300000 ms = 5 min 60000 ms=1 min
    public void autoCancelPendingReservations() {
        cancelUnpaidReservations();
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
    public List<ReservationDTO> getReservationsByUserAndStatus(Long userId, String statusStr) {
        User current = authService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // USER → solo las suyas
        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes ver reservas de otros usuarios.");
        }

        // ADMIN → solo dentro del ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(target);
        }

        ReservationStatus status;
        try {
            status = ReservationStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de reserva no válido: " + statusStr);
        }

        // 🔹 1. Reservas creadas por el usuario
        List<Reservation> creadas = reservationRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(userId, status);

        // 🔹 2. Reservas donde el usuario fue invitado y aceptó
        List<Long> idsAceptadas = invitationRepository
                .findByReceiver_IdAndStatus(userId, InvitationStatus.ACCEPTED)
                .stream()
                .map(inv -> inv.getReservation().getId())
                .distinct()
                .toList();

        List<Reservation> comoInvitado = idsAceptadas.isEmpty()
                ? List.of()
                : reservationRepository.findByIdInAndStatusOrderByCreatedAtDesc(idsAceptadas, status);

        // 🔹 3. Unir ambas sin duplicados
        List<Reservation> todas = new java.util.ArrayList<>();
        todas.addAll(creadas);
        todas.addAll(comoInvitado);

        List<Reservation> sinDuplicados = todas.stream()
                .distinct()
                .toList();

        // 🔹 4. Mapear a DTOs
        return sinDuplicados.stream()
                .map(res -> StatustoDTO(res, userId))
                .toList();
    }



    @Transactional
    public void cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        User current = authService.getCurrentUser();

        // Usuario solo puede cancelar su reserva
        if (authService.isUser() && !reservation.getUser().getId().equals(current.getId())) {
            throw new AccessDeniedException("No puedes cancelar reservas de otros usuarios.");
        }

        // Admin solo si pertenece al ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(reservation.getAyuntamiento());
        }

        if (reservation.getPayment() != null) {
            paymentRepository.delete(reservation.getPayment());
        }

        if (!reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permiso para cancelar esta reserva.");
        }

        // 🧹 Eliminar las invitaciones relacionadas primero
        invitationRepository.deleteAllByReservationId(reservationId);

        // 🧾 Si la reserva tenía un pago, también puedes borrarlo opcionalmente
        if (reservation.getPayment() != null) {
            paymentRepository.delete(reservation.getPayment());
        }

        // 📩 Preparar y enviar correo a todos los jugadores
        // Obtener los correos de todos los jugadores involucrados
        List<User> jugadores = new ArrayList<>(reservation.getJugadores());
        String creador = reservation.getUser().getFullName();
        LocalDateTime fechaHora = reservation.getStartTime();

        String fechaFormateada = fechaHora.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", new Locale("es", "ES")));

        // Crear HTML del correo
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

        // Enviar el correo a todos los jugadores
        for (User jugador : jugadores) {
            if (jugador.getEmail() != null && !jugador.getEmail().isEmpty()) {
                emailService.sendHtmlEmail(
                        jugador.getEmail(),
                        "Reserva cancelada - " + fechaFormateada,
                        html
                );
            }
        }

        // 🗑️ Finalmente elimina la reserva
        reservationRepository.delete(reservation);
    }

    @Transactional
    public void updateReservationStatusToCanceled(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        // 🔒 Verificar que el usuario sea el dueño de la reserva
        if (!reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permiso para cancelar esta reserva.");
        }

        // 🧹 Eliminar invitaciones relacionadas (si corresponde)
        invitationRepository.deleteAllByReservationId(reservationId);

        // 💳 Si hay un pago asociado, podrías marcarlo como anulado
        if (reservation.getPayment() != null) {
            reservation.getPayment().setStatus(Payment.Status.CANCELED);
            paymentRepository.save(reservation.getPayment());
        }

        // 🔁 Cambiar estado de la reserva
        reservation.setStatus(ReservationStatus.CANCELED);

        // 📩 Preparar y enviar correo a todos los jugadores
        // Obtener los correos de todos los jugadores involucrados
        List<User> jugadores = new ArrayList<>(reservation.getJugadores());
        String creador = reservation.getUser().getFullName();
        LocalDateTime fechaHora = reservation.getStartTime();

        String fechaFormateada = fechaHora.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", new Locale("es", "ES")));

        // Crear HTML del correo
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

        // Enviar el correo a todos los jugadores
        for (User jugador : jugadores) {
            if (jugador.getEmail() != null && !jugador.getEmail().isEmpty()) {
                emailService.sendHtmlEmail(
                        jugador.getEmail(),
                        "Reserva cancelada - " + fechaFormateada,
                        html
                );
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
    @Transactional(readOnly = true)
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
            reservations = reservationRepository.findByStartTimeBetweenAndAyuntamientoId(
                    startOfDay,
                    endOfDay,
                    current.getAyuntamiento().getId()
            );
        }

        LocalTime opening = LocalTime.of(8, 0);
        LocalTime closing = LocalTime.of(23, 0);

        List<LocalTime> allSlots = new ArrayList<>();
        for (LocalTime time = opening; time.isBefore(closing); time = time.plusMinutes(90)) {
            allSlots.add(time);
        }

        // ✅ Resolver correctamente el usuario actual (username o email)
        Long currentUserId = resolveCurrentUserId();

        List<HourSlotDTO> result = new ArrayList<>();

        for (LocalTime slot : allSlots) {
            Optional<Reservation> reservationOpt = reservations.stream()
                    .filter(r -> r.getStartTime().toLocalTime().equals(slot))
                    .findFirst();

            if (reservationOpt.isPresent()) {
                Reservation reservation = reservationOpt.get();

                String status = switch (reservation.getStatus()) {
                    case PENDING   -> "PENDING_PAYMENT";
                    case CONFIRMED -> "PAID";
                    default        -> "AVAILABLE";
                };

                List<PlayerInfoDTO> players = reservation.getJugadores().stream()
                        .filter(p -> !reservation.isPlayerRejected(p))
                        .map(p -> {
                            boolean accepted = invitationRepository
                                    .findByReservationAndReceiver(reservation, p)
                                    .map(inv -> inv.getStatus() == InvitationStatus.ACCEPTED)
                                    .orElse(true); // el creador o jugadores directos siempre true

                            return new PlayerInfoDTO(
                                    p.getId(),
                                    p.getUsername(),
                                    p.getProfileImageUrl() != null ? p.getProfileImageUrl()
                                            : "https://ui-avatars.com/api/?name=" + p.getUsername(),
                                    accepted
                            );
                        })
                        .toList();

                boolean esCreador = currentUserId != null
                        && reservation.getUser() != null
                        && reservation.getUser().getId().equals(currentUserId);

                BigDecimal precio = calcularPrecio(slot, ay);

                HourSlotDTO dto = new HourSlotDTO(slot, status, reservation.isPublic(), players, esCreador,reservation.getId(),precio);
                result.add(dto);

            } else {
                BigDecimal precio = calcularPrecio(slot, ay);
                result.add(new HourSlotDTO(slot, "AVAILABLE", false, List.of(), false, null,precio));
            }

        }

        if (date.equals(LocalDate.now())) {
            LocalTime now = LocalTime.now();
            result.forEach(slotDto -> {
                if (slotDto.getTime().isBefore(now)) {
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
    public List<ReservationDTO> getReservationsByUser(Long userId) {

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

        return reservations.stream().map(this::toDTO).toList();
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

        // 🔹 También incluir reservas donde fue invitado y aún no aceptó (opcional)
        List<Long> invitacionesPendientes = invitationRepository
                .findByReceiver_IdAndStatus(userId, InvitationStatus.PENDING)
                .stream()
                .map(inv -> inv.getReservation().getId())
                .distinct()
                .toList();

        List<Reservation> comoInvitado = invitacionesPendientes.isEmpty()
                ? List.of()
                : reservationRepository.findByIdInAndStatus(invitacionesPendientes, ReservationStatus.PENDING);

        // 🔹 Unir ambas listas sin duplicar
        List<Reservation> todas = new ArrayList<>();
        todas.addAll(pendientes);
        todas.addAll(comoInvitado);

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

        // ❌ Eliminar al jugador
        reservation.getJugadores().removeIf(u -> u.getId().equals(user.getId()));
        reservationRepository.save(reservation);

        // 🔁 Marcar invitación como REJECTED si existe
        // 🔁 Buscar todas las invitaciones y marcarlas como REJECTED
        List<Invitation> invitaciones = invitationRepository.findAllByReservationAndReceiver(reservation, user);

        if (!invitaciones.isEmpty()) {
            invitaciones.forEach(inv -> {
                inv.setStatus(com.example.PadelCaleruela.model.InvitationStatus.REJECTED);
                invitationRepository.save(inv);
            });
        }


        return "Has abandonado la partida correctamente.";
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
