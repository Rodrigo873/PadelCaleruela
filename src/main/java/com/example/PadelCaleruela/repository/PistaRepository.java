package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Pista;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PistaRepository extends JpaRepository<Pista, Long> {
    List<Pista> findByAyuntamientoId(Long ayuntamientoId);

}
