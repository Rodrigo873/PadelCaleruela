package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.*;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.UserRepository;
import com.example.PadelCaleruela.service.InvitationService;
import com.example.PadelCaleruela.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "http://localhost:4200")
public class ReservationController {

    private final ReservationService reservationService;

    private final InvitationService invitationService;

    private final UserRepository userRepository;


    public ReservationController(ReservationService service,InvitationService invitationService,UserRepository userRepository) {
        this.reservationService = service;
        this.invitationService=invitationService;
        this.userRepository=userRepository;
    }

    // Crear reserva
    @PostMapping
    public ResponseEntity<ReservationDTO> create(@RequestBody ReservationDTO dto) {
        return ResponseEntity.ok(reservationService.createReservation(dto));
    }

    // 🟣 Invitar jugadores a una reserva existente
    @PostMapping("/{reservationId}/invite")
    public ResponseEntity<Map<String, String>> inviteToReservation(
            @PathVariable Long reservationId,
            @RequestParam Long senderId,
            @RequestBody List<Long> invitedIds) {

        String message = reservationService.inviteToReservation(reservationId, senderId, invitedIds);
        return ResponseEntity.ok(Map.of("message", message));
    }


    // Controller
    @GetMapping("/reservations/public-id")
    public ResponseEntity<Long> getPublicReservationId(
            @RequestParam String date,    // "YYYY-MM-DD"
            @RequestParam String time     // "HH:mm"
    ) {
        Long id = reservationService.findPublicReservationId(LocalDate.parse(date), LocalTime.parse(time));
        if (id == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(id);
    }


    @GetMapping("/infoReservation")
    public List<InfoReservationDTO> getAllInfo() {
        return reservationService.getAllInfoReservation();
    }

    // Obtener reservas del día
    @GetMapping("/day")
    public ResponseEntity<List<ReservationDTO>> getReservationsForDay(@RequestParam String date) {
        LocalDate parsedDate = LocalDate.parse(date);
        return ResponseEntity.ok(reservationService.getReservationsForDay(parsedDate));
    }

    // eliminar reserva
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<String> cancel(@PathVariable Long reservationId, @RequestParam Long userId) {
        reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity.ok("Reserva cancelada correctamente.");
    }

    //Cancelar reserva
    @PutMapping("/{reservationId}/cancel")
    public ResponseEntity<String> updateStatusToCanceled(
            @PathVariable Long reservationId,
            @RequestParam Long userId) {

        reservationService.updateReservationStatusToCanceled(reservationId, userId);
        return ResponseEntity.ok("Estado de la reserva actualizado a CANCELED correctamente.");
    }


    //Horas disponibles de un dia
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getDailySlots(@RequestParam String date) {
        LocalDate parsedDate = LocalDate.parse(date);
        List<HourSlotDTO> slots = reservationService.getAvailableHours(parsedDate);

        return ResponseEntity.ok(Map.of(
                "date", parsedDate.toString(),
                "slots", slots
        ));
    }

    @GetMapping("/confirmed/count/{userId}")
    public ResponseEntity<Integer> getConfirmedReservationCount(@PathVariable Long userId) {
        int count = reservationService.getConfirmedReservationsCount(userId);
        return ResponseEntity.ok(count);
    }



    @GetMapping("/user/{userId}")
    public List<ReservationWithPistaDTO> getReservationsByUserAndStatus(
            @PathVariable Long userId,
            @RequestParam(required = false) String status
    ) {
        if (status == null || status.isEmpty()) {
            return reservationService.getReservationsByUser(userId);
        } else {
            return reservationService.getReservationsByUserAndStatus(userId, status);
        }
    }

    /** 🔹 Endpoint para obtener los jugadores con su estado */
    @GetMapping("/{reservationId}/jugadores")
    public ResponseEntity<List<ReservationPlayerDTO>> getJugadoresPorReserva(@PathVariable Long reservationId) {
        List<ReservationPlayerDTO> jugadores = reservationService.getJugadoresPorReserva(reservationId);
        return ResponseEntity.ok(jugadores);
    }

    /**
     * 🔹 Obtener todas las reservas confirmadas de un usuario
     */
    @GetMapping("/user/{userId}/confirmed")
    public List<ReservationDTO> getConfirmedReservationsByUser(@PathVariable Long userId) {
        return reservationService.getConfirmedReservationsByUser(userId);
    }

    //Unirse a reserva publica
    @PutMapping("/{reservationId}/join")
    public ResponseEntity<String> joinPublicReservation(
            @PathVariable Long reservationId,
            Principal principal
    ) {
        String message = invitationService.joinPublicReservationWithInvitation(reservationId, principal.getName());
        return ResponseEntity.ok(message);
    }

    //Abandonar reserva
    @PutMapping("/{reservationId}/leave")
    public ResponseEntity<String> leavePublicReservation(
            @PathVariable Long reservationId,
            Principal principal
    ) {
        String message = reservationService.leavePublicReservation(reservationId, principal.getName());
        return ResponseEntity.ok(message);
    }



    //Ver reservas publicas
    @GetMapping("/public")
    public ResponseEntity<List<ReservationDTO>> getPublicAvailableReservations(@RequestParam Long userId) {
        List<ReservationDTO> available = reservationService.getAvailablePublicReservations(userId);
        return ResponseEntity.ok(available);
    }

    //Para animaciones de notificaciones
    @GetMapping("/pending/{userId}")
    public ResponseEntity<List<ReservationDTO>> getPendingReservations(@PathVariable Long userId) {
        List<ReservationDTO> pendientes = reservationService.getPendingReservations(userId);
        return ResponseEntity.ok(pendientes);
    }


    @GetMapping("/{reservaId}/pista")
    public ResponseEntity<?> getPistaDeReserva(
            @PathVariable Long reservaId,
            @RequestParam Long userId) {

        System.out.println("🔍 Controller: solicitando pista de reserva " + reservaId + " por user " + userId);

        return ResponseEntity.ok(
                reservationService.getPistaVisibleParaUsuario(reservaId, userId)
        );
    }

    @GetMapping("/admin/last-confirmed")
    public ResponseEntity<List<ReservationSummaryDTO>> getLastConfirmedForAdmin() {
        return ResponseEntity.ok(reservationService.getLast10ConfirmedReservationsForAyuntamiento());
    }


    @DeleteMapping("/{reservationId}/kick/{userId}")
    public ResponseEntity<?> kickPlayer(
            @PathVariable Long reservationId,
            @PathVariable Long userId,
            Principal principal) {

        try {
            String result = reservationService.kickPlayerFromReservation(
                    reservationId,
                    userId,
                    principal.getName()
            );

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }


}
