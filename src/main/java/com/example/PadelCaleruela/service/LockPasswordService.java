package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LockPasswordDTO;
import com.example.PadelCaleruela.model.LockPassword;
import com.example.PadelCaleruela.repository.LockPasswordRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LockPasswordService {

    private final LockPasswordRepository lockPasswordRepository;

    public List<LockPasswordDTO> getPasswordsByReservation(Long reservationId) {

        List<LockPassword> passwords = lockPasswordRepository.findByReservationId(reservationId);

        return passwords.stream().map(lp -> {
            LockPasswordDTO dto = new LockPasswordDTO();
            dto.setLockId(lp.getLock().getId());
            dto.setBleName(lp.getLock().getBleName());
            dto.setPassword(lp.getPassword());
            dto.setStartTime(lp.getStartTime());
            dto.setEndTime(lp.getEndTime());
            return dto;
        }).toList();
    }

    // 🔥 Ejecuta cada minuto
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void cleanExpiredPasswords() {

        LocalDateTime now = LocalDateTime.now();

        // 5 minutos después de endTime
        LocalDateTime threshold = now.minusMinutes(5);

        List<LockPassword> expired = lockPasswordRepository.findAllByEndTimeBefore(threshold);

        if (!expired.isEmpty()) {
            lockPasswordRepository.deleteAll(expired);
            System.out.println("🧹 Eliminadas " + expired.size() + " contraseñas expiradas.");
        }
    }
}
