package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Invitation;
import com.example.PadelCaleruela.model.InvitationStatus;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.model.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findByReceiverAndStatus(User receiver, InvitationStatus status);
    List<Invitation> findByReservationId(Long reservationId);
    Optional<Invitation> findByReservationAndReceiver(Reservation reservation, User receiver);
    boolean existsByReservationIdAndReceiverId(Long reservationId, Long receiverId);

    @Query("""
        SELECT COUNT(i)
        FROM Invitation i
        WHERE i.receiver.id = :userId
          AND i.status = 'PENDING'
    """)
    long countPendingByUserId(@Param("userId") Long userId);

    // Última invitación PENDING de ese usuario para esa reserva (por si hubiese varias históricas)
    Optional<Invitation> findTopByReservation_IdAndReceiver_IdAndStatusOrderByIdDesc(
            Long reservationId,
            Long receiverId,
            InvitationStatus status
    );

    Optional<Invitation> findByReservationIdAndReceiverId(Long reservationId, Long receiverId);

    List<Invitation> findAllByReservationIdAndReceiverIdOrderByCreatedAtAsc(Long reservationId, Long receiverId);


    List<Invitation> findByReceiver_IdAndStatus(Long userId, InvitationStatus status);

    @Modifying
    @Transactional
    @Query("DELETE FROM Invitation i WHERE i.reservation.id = :reservationId")
    void deleteAllByReservationId(@Param("reservationId") Long reservationId);

    @Query("SELECT i FROM Invitation i WHERE i.reservation = :reservation AND i.receiver = :receiver")
    List<Invitation> findAllByReservationAndReceiver(@Param("reservation") Reservation reservation,
                                                     @Param("receiver") User receiver);


}
