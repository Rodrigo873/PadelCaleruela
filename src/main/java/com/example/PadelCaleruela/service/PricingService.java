package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Tarifa;
import com.example.PadelCaleruela.model.TarifaFranja;
import com.example.PadelCaleruela.repository.TarifaFranjaRepository;
import com.example.PadelCaleruela.repository.TarifaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final TarifaRepository tarifaRepo;
    private final TarifaFranjaRepository franjaRepo;

    public BigDecimal calcularPrecio(LocalDateTime start, Ayuntamiento ay) {

        int hora = start.getHour();

        // 1️⃣ Buscar franja especial
        List<TarifaFranja> franjas = franjaRepo.findByAyuntamiento(ay);

        for (TarifaFranja f : franjas) {
            if (hora >= f.getHoraInicio() && hora < f.getHoraFin()) {
                return f.getPrecio();
            }
        }

        // 2️⃣ Precio base
        Tarifa tarifa = tarifaRepo.findByAyuntamiento(ay)
                .orElseThrow(() -> new RuntimeException("No hay tarifa base definida para este ayuntamiento"));

        return tarifa.getPrecioBase();
    }
}
