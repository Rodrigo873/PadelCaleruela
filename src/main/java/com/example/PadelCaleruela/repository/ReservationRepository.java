package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.ReservationStatus;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByStartTimeBetweenAndAyuntamientoId(
            LocalDateTime start, LocalDateTime end, Long ayuntamientoId);
    @Query("SELECT r FROM Reservation r WHERE r.isPublic = true AND r.startTime > :now AND r.ayuntamiento.id = :ayId")
    List<Reservation> findPublicAvailableReservationsByAyuntamiento(LocalDateTime now, Long ayId);
    List<Reservation> findByAyuntamientoId(Long ayuntamientoId);
    @Query("SELECT r FROM Reservation r WHERE r.pista.id = :pistaId AND DATE(r.startTime) = :date")
    List<Reservation> findByPistaIdAndDate(Long pistaId, LocalDate date);

    @Query("""
    SELECT COUNT(r) 
    FROM Reservation r 
    LEFT JOIN r.jugadores j
    WHERE (r.user.id = :userId OR j.id = :userId)
    AND r.status = 'CONFIRMED'
    """)
    int countConfirmedByUserId(@Param("userId") Long userId);

    @Query("""
    SELECT r FROM Reservation r
    WHERE r.status = com.example.PadelCaleruela.model.ReservationStatus.CONFIRMED
    AND r.startTime < :now
    AND r.ayuntamiento.id = :ayId
    ORDER BY r.startTime DESC
    """)
    List<Reservation> findLastConfirmedBeforeNow(@Param("now") LocalDateTime now,
                                                 @Param("ayId") Long ayId,
                                                 org.springframework.data.domain.Pageable pageable);



    List<Reservation> findByUserOrderByStartTimeDesc(User user);
    Optional<Reservation> findFirstByStartTimeAndStatusNotAndIsPublicTrue(
            LocalDateTime startTime, ReservationStatus status);

    @Query("SELECT r FROM Reservation r WHERE r.paid = false AND r.createdAt < :limit AND r.status = com.example.PadelCaleruela.model.ReservationStatus.PENDING")
    List<Reservation> findByPaidFalseAndCreatedAtBefore(@Param("limit") LocalDateTime limit);

    // Buscar por usuario y estado
    List<Reservation> findByUser_IdAndStatus(Long userId, ReservationStatus status);

    List<Reservation> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
    SELECT r.user.id
    FROM Reservation r
    WHERE r.status = 'CONFIRMED'
    GROUP BY r.user.id
    HAVING COUNT(r) > 0
    ORDER BY COUNT(r) DESC
""")
    List<Long> findTopPlayersByConfirmedReservations();

    @Query("""
        SELECT r FROM Reservation r
        WHERE (r.user.id = :userId OR :userId IN (
            SELECT j.id FROM r.jugadores j
        ))
        AND r.status = 'CONFIRMED'
    """)
    List<Reservation> findConfirmedByUserId(Long userId);

    @Query("SELECT r FROM Reservation r WHERE r.id IN :ids AND r.status = :status")
    List<Reservation> findByIdInAndStatus(@Param("ids") List<Long> ids, @Param("status") ReservationStatus status);

    @Query("SELECT r FROM Reservation r " +
            "WHERE r.isPublic = true " +
            "AND (r.status = 'PENDING' OR r.status = 'CONFIRMED') " +
            "AND r.startTime > :now")
    List<Reservation> findPublicAvailableReservations(@Param("now") LocalDateTime now);

    List<Reservation> findByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, ReservationStatus status);
    List<Reservation> findByIdInAndStatusOrderByCreatedAtDesc(List<Long> ids, ReservationStatus status);

    List<Reservation> findByPaidFalseAndStatusAndCreatedAtBetween(
            ReservationStatus status,
            LocalDateTime start,
            LocalDateTime end
    );




}
