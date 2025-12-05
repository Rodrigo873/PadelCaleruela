package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.LockPassword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LockPasswordRepository extends JpaRepository<LockPassword, Long> {

    List<LockPassword> findByReservationId(Long reservationId);

    List<LockPassword> findByLockId(Long lockId);
    List<LockPassword> findAllByEndTimeBefore(LocalDateTime time);

}
