package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.ReservationDTO;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.ReservationRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    // 🔹 Crear reserva con duración fija de 1h30min
    public ReservationDTO createReservation(ReservationDTO dto) {
        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = start.plusMinutes(90);

        boolean overlaps = reservationRepository.existsByTimeRange(start, end);
        if (overlaps) {
            throw new RuntimeException("Ya existe una reserva en ese horario.");
        }

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setPaid(false);
        reservation.setStatus(ReservationStatus.PENDING);

        Reservation saved = reservationRepository.save(reservation);
        return toDTO(saved);
    }

    // 🔹 Cancelar reservas no pagadas después de 15 minutos
    @Transactional
    public void cancelUnpaidReservations() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(15); // margen de 15 min
        List<Reservation> toCancel = reservationRepository.findByPaidFalseAndCreatedAtBefore(limit);

        for (Reservation r : toCancel) {
            r.setStatus(ReservationStatus.CANCELED);
        }

        if (!toCancel.isEmpty()) {
            reservationRepository.saveAll(toCancel);
            System.out.println("🔸 Canceladas automáticamente " + toCancel.size() + " reservas impagas.");
        }
    }

    // 🔹 Ejecutar automáticamente cada 5 minutos
    @Scheduled(fixedRate = 300000) // 300000 ms = 5 min
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


    public void cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permiso para cancelar esta reserva.");
        }

        reservationRepository.delete(reservation);
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

        // Eliminar las horas ya reservadas
        Set<LocalTime> reservedSlots = reservations.stream()
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



    private ReservationDTO toDTO(Reservation reservation) {
        ReservationDTO dto = new ReservationDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setStartTime(reservation.getStartTime());
        dto.setEndTime(reservation.getEndTime());
        dto.setCreatedAt(reservation.getCreatedAt());
        return dto;
    }
}
