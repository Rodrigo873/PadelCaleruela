package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Ayuntamiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AyuntamientoRepository extends JpaRepository<Ayuntamiento, Long> {
    Optional<Ayuntamiento> findByCodigoPostal(String codigoPostal);

    List<Ayuntamiento> findByActivoTrue();

    List<Ayuntamiento> findByActivoTrueAndPublicoTrue();


}

