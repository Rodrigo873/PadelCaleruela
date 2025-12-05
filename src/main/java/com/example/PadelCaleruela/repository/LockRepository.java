package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LockRepository extends JpaRepository<Lock, Long> {

    Optional<Lock> findByPistaId(Long pistaId);

    Optional<Lock> findByDeviceNumber(String deviceNumber);
    List<Lock> findLocksByPistaId(Long pistaId);

    List<Lock> findAllByPistaId(Long pistaId);

}
