package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.*;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.InvitationRepository;
import com.example.PadelCaleruela.repository.PaymentRepository;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // 🔹 Crear reserva con duración fija de 1h30min
    public ReservationDTO createReservation(ReservationDTO dto) {
        System.out.println(dto);
        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = start.plusMinutes(90);



        // 🔹 Buscar al usuario creador
        User creator = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 🔹 Crear la nueva reserva
        Reservation reservation = new Reservation();
        reservation.setUser(creator);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setPaid(false);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPublic(dto.isPublic());

        // 🔹 Añadir jugadores (el creador siempre incluido)
        Set<User> jugadores = new HashSet<>();
        jugadores.add(creator);

        if (dto.getJugadores() != null && !dto.getJugadores().isEmpty()) {
            List<Long> invitedIds = dto.getJugadores().stream()
                    .map(UserDTO::getId)
                    .filter(id -> id != null && !id.equals(creator.getId()))
                    .toList();

            if (!invitedIds.isEmpty()) {
                List<User> invitados = userRepository.findAllById(invitedIds);
                jugadores.addAll(invitados);
            }
        }

        reservation.setJugadores(jugadores);

        // 🔹 Guardar en base de datos
        Reservation saved = reservationRepository.save(reservation);

        saved.getJugadores().size();
        if (dto.getJugadores() != null && !dto.getJugadores().isEmpty()) {
            List<Long> invitedIds = dto.getJugadores().stream()
                    .map(UserDTO::getId)
                    .filter(id -> id != null && !id.equals(creator.getId()))
                    .toList();

            if (!invitedIds.isEmpty()) {
                List<User> invitados = userRepository.findAllById(invitedIds);
                jugadores.addAll(invitados);

                // Crear invitaciones pendientes
                for (User invited : invitados) {
                    Invitation inv = new Invitation();
                    inv.setReservation(reservation);
                    inv.setSender(creator);
                    inv.setReceiver(invited);
                    inv.setStatus(InvitationStatus.PENDING);
                    invitationRepository.save(inv);
                }
            }
        }

        // 🔹 Convertir a DTO y devolver
        return toDTO(saved);
    }

    public List<InfoReservationDTO> getAllInfoReservation() {
        return reservationRepository.findAll()
                .stream()
                .map(this::toDTOinfo)
                .collect(Collectors.toList());
    }


    // 🔹 Cancelar reservas no pagadas después de 15 minutos
    @Transactional
    public void cancelUnpaidReservations() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(15); // margen de 15 min
        List<Reservation> toCancel = reservationRepository.findByPaidFalseAndCreatedAtBefore(limit);

        for (Reservation r : toCancel) {
            // 🧹 Eliminar invitaciones asociadas antes de cancelar
            invitationRepository.deleteAllByReservationId(r.getId());

            // ⚙️ Cambiar estado a CANCELADA
            r.setStatus(ReservationStatus.CANCELED);
        }

        if (!toCancel.isEmpty()) {
            reservationRepository.saveAll(toCancel);
            System.out.println("🔸 Canceladas automáticamente " + toCancel.size() + " reservas impagas.");
        }
    }


    // 🔹 Ejecutar automáticamente cada 1 minutos
    @Scheduled(fixedRate = 60000) // 300000 ms = 5 min 60000 ms=1 min
    public void autoCancelPendingReservations() {
        cancelUnpaidReservations();
    }


    public List<ReservationDTO> getReservationsForDay(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        return reservationRepository.findByStartTimeBetween(startOfDay, endOfDay)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ReservationService
    @Transactional(readOnly = true)
    public List<ReservationDTO> getReservationsByUserAndStatus(Long userId, String statusStr) {
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
        try {
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

        } catch (MessagingException e) {
            System.err.println("⚠️ Error al enviar correo de cancelación: " + e.getMessage());
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
        try {
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

        } catch (MessagingException e) {
            System.err.println("⚠️ Error al enviar correo de cancelación: " + e.getMessage());
        }


        reservationRepository.save(reservation);
    }



    // 🔹 Devuelve las horas disponibles de un día
    // 🔹 Genera los slots disponibles de un día
    public List<LocalTime> getAvailableHours(LocalDate date) {
        // Horario de apertura
        LocalTime opening = LocalTime.of(8, 0);
        LocalTime closing = LocalTime.of(23, 0);

        // Crear slots de 1h30min
        List<LocalTime> allSlots = new ArrayList<>();
        for (LocalTime time = opening; time.isBefore(closing); time = time.plusMinutes(90)) {
            allSlots.add(time);
        }

        // Filtrar si es hoy: quitar horas pasadas
        if (date.equals(LocalDate.now())) {
            LocalTime now = LocalTime.now();
            allSlots = allSlots.stream()
                    .filter(slot -> slot.isAfter(now))
                    .collect(Collectors.toList());
        }

        // Obtener reservas del día
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        List<Reservation> reservations = reservationRepository.findByStartTimeBetween(startOfDay, endOfDay);

        // Eliminar solo las horas de reservas activas (no canceladas)
        Set<LocalTime> reservedSlots = reservations.stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELED)
                .map(r -> r.getStartTime().toLocalTime())
                .collect(Collectors.toSet());


        return allSlots.stream()
                .filter(slot -> !reservedSlots.contains(slot))
                .collect(Collectors.toList());
    }

    //Reservas de usuario
    public List<ReservationDTO> getReservationsByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Reservation> reservations = reservationRepository.findByUserOrderByStartTimeDesc(user);

        return reservations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> getPendingReservations(Long userId) {
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
        Reservation reserva = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        List<ReservationPlayerDTO> jugadores = new ArrayList<>();

        // ✅ Añadir el creador de la reserva (siempre aceptado)
        jugadores.add(new ReservationPlayerDTO(
                reserva.getUser().getUsername(),
                reserva.getUser().getFullName(),
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

    @Transactional
    public String joinPublicReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

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
        LocalDateTime now = LocalDateTime.now();
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

            dto.setJugadores(res.getJugadores().stream().map(j -> {
                UserDTO userDTO = new UserDTO();
                userDTO.setId(j.getId());
                userDTO.setUsername(j.getUsername());
                userDTO.setFullName(j.getFullName());
                userDTO.setProfileImageUrl(j.getProfileImageUrl());
                return userDTO;
            }).toList());

            return dto;
        }).toList();
    }






}
