package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.TarifaFranja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TarifaFranjaRepository extends JpaRepository<TarifaFranja, Long> {
    List<TarifaFranja> findByAyuntamiento(Ayuntamiento ay);
    List<TarifaFranja> findByAyuntamientoId(Long ayuntamientoId);

}

