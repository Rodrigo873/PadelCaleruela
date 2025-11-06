// PaymentRepository.java
package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUser_Id(Long userId);
    boolean existsByReservation_Id(Long reservationId);
    Payment findByReservation_Id(Long reservationId);


}
