package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
    SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
    FROM Reservation r
    WHERE (r.startTime < :end AND r.endTime > :start)
""")
    boolean existsByTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Reservation> findByUserOrderByStartTimeDesc(User user);

    @Query("SELECT r FROM Reservation r WHERE r.paid = false AND r.createdAt < :limit AND r.status = com.example.PadelCaleruela.model.ReservationStatus.PENDING")
    List<Reservation> findByPaidFalseAndCreatedAtBefore(@Param("limit") LocalDateTime limit);

    List<Reservation> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
}
