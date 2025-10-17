package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.ReservationDTO;
import com.example.PadelCaleruela.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "http://localhost:4200")
public class ReservationController {

    private final ReservationService reservationService;


    public ReservationController(ReservationService service) {
        this.reservationService = service;
    }

    // Crear reserva
    @PostMapping
    public ResponseEntity<ReservationDTO> create(@RequestBody ReservationDTO dto) {
        return ResponseEntity.ok(reservationService.createReservation(dto));
    }

    // Obtener reservas del día
    @GetMapping("/day")
    public ResponseEntity<List<ReservationDTO>> getReservationsForDay(@RequestParam String date) {
        LocalDate parsedDate = LocalDate.parse(date);
        return ResponseEntity.ok(reservationService.getReservationsForDay(parsedDate));
    }

    // Cancelar reserva
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<String> cancel(@PathVariable Long reservationId, @RequestParam Long userId) {
        reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity.ok("Reserva cancelada correctamente.");
    }

    //Horas disponibles de un dia
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailableSlots(@RequestParam String date) {
        LocalDate parsedDate = LocalDate.parse(date);
        List<LocalTime> available = reservationService.getAvailableHours(parsedDate);

        return ResponseEntity.ok(Map.of(
                "date", parsedDate.toString(),
                "availableSlots", available
        ));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReservationDTO>> getUserReservations(@PathVariable Long userId) {
        List<ReservationDTO> reservations = reservationService.getReservationsByUser(userId);
        return ResponseEntity.ok(reservations);
    }

}
