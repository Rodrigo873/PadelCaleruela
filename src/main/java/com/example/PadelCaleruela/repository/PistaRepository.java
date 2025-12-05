package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.dto.PistaListDTO;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Pista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PistaRepository extends JpaRepository<Pista, Long> {
    List<Pista> findByAyuntamientoId(Long ayuntamientoId);

    @Query("""
    SELECT new com.example.PadelCaleruela.dto.PistaListDTO(
        p.id,
        p.nombre,
        p.activa,
        COUNT(l.id),
        p.ayuntamiento.imageUrl,
        p.ayuntamiento.nombre
    )
    FROM Pista p
    LEFT JOIN p.locks l
    GROUP BY p.id, p.nombre, p.activa, p.ayuntamiento.imageUrl, p.ayuntamiento.nombre
    """)
    List<PistaListDTO> listarResumen();



    List<Pista> findByAyuntamientoIdAndActivaTrue(Long ayuntamientoId);


}
