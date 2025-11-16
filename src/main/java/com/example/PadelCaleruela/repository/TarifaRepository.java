package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Tarifa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TarifaRepository extends JpaRepository<Tarifa, Long> {
    Optional<Tarifa> findByAyuntamiento(Ayuntamiento ay);
    Optional<Tarifa> findByAyuntamientoId(Long ayuntamientoId);

}

