package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.WelockClient;
import com.example.PadelCaleruela.dto.LockRegisterDTO;
import com.example.PadelCaleruela.dto.LockSimpleDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.DeviceTokenRepository;
import com.example.PadelCaleruela.repository.LockRepository;
import com.example.PadelCaleruela.repository.PistaRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class LockService {

    private final LockRepository lockRepository;
    private final ReservationService reservaService;

    private final UserRepository userRepo;

    private final WelockClient welockClient;
    private final PistaRepository pistaRepo;
    private final AyuntamientoService ayuntamientoService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final DeviceTokenRepository tokenRepository;

    public LockService(LockRepository lockRepository,ReservationService reservaService,UserRepository userRepository,
                       WelockClient welockClient, PistaRepository pistaRepository,AyuntamientoService ayuntamientoService,
                       EmailService emailService,NotificationService notificationService,DeviceTokenRepository tokenRepository) {
        this.lockRepository = lockRepository;
        this.reservaService=reservaService;
        this.userRepo=userRepository;
        this.welockClient=welockClient;
        this.pistaRepo=pistaRepository;
        this.ayuntamientoService=ayuntamientoService;
        this.emailService=emailService;
        this.notificationService=notificationService;
        this.tokenRepository=tokenRepository;
    }

    public Optional<Lock> findByPistaId(Long pistaId) {
        return lockRepository.findByPistaId(pistaId);
    }

    public Lock save(Lock lock) {
        return lockRepository.save(lock);
    }

    public List<Lock> findAllByPistaId(Long pistaId) {
        return lockRepository.findAllByPistaId(pistaId);
    }

    @Transactional
    public List<LockRegisterDTO> getLocksByPista(Long pistaId) {

        System.out.println("🔎 Service: obteniendo cerraduras de pista " + pistaId);

        List<Lock> locks = lockRepository.findAllByPistaId(pistaId);

        if (locks.isEmpty()) {
            throw new RuntimeException("La pista no tiene cerraduras asociadas");
        }

        return locks.stream().map(lock -> {
            LockRegisterDTO dto = new LockRegisterDTO();
            dto.setId(lock.getId());
            dto.setName(lock.getName());
            dto.setDeviceNumber(lock.getDeviceNumber());
            dto.setBleName(lock.getBleName());
            dto.setDeviceMAC(lock.getDeviceMAC());
            dto.setPistaId(lock.getPista().getId());
            return dto;
        }).toList();
    }

    public List<LockSimpleDTO> getAllLocksSimple() {

        List<Lock> locks = lockRepository.findAll();

        return locks.stream()
                .map(l -> new LockSimpleDTO(
                        l.getId(),
                        l.getName(),
                        l.getPista().getNombre(),
                        l.getPista().getAyuntamiento().getNombre()  // 🆕NUEVO
                ))
                .toList();
    }



    public Lock registerAndSaveLock(String name,String deviceNumber, String bleName, String deviceMAC, Long pistaId) {

        // 1. Registrar dispositivo en Welock
        welockClient.registerDevice(deviceNumber, bleName, null);

        // 2. Buscar pista
        Pista pista = pistaRepo.findById(pistaId)
                .orElseThrow(() -> new RuntimeException("Pista no encontrada"));

        // 3. Crear lock para tu base de datos
        Lock lock = new Lock();
        lock.setName(name);
        lock.setDeviceNumber(deviceNumber);
        lock.setBleName(bleName);
        lock.setDeviceMAC(deviceMAC);
        lock.setPista(pista);

        // 4. Guardar BD
        return lockRepository.save(lock);
    }

    public void notifyTownHallDoorWasOpen(Long userId) {

        // 1. Buscar usuario
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        // 2. Ayuntamiento del usuario
        Ayuntamiento ayuntamiento = user.getAyuntamiento();
        if (ayuntamiento == null) {
            return;
        }

        // 3. Obtener todos los administradores del ayuntamiento
        List<User> admins = ayuntamiento.getUsuarios().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .toList();

        if (admins.isEmpty()) {
        }

        // 4. Email del ayuntamiento (si lo tiene)
        String emailAyuntamiento = ayuntamiento.getEmail();

        // 5. Obtener token más reciente de cada admin
        List<String> latestTokens = admins.stream()
                .map(admin -> {
                    List<DeviceToken> tokens = tokenRepository.findByUserId(admin.getId());

                    return tokens.stream()
                            .filter(DeviceToken::isActive)
                            .sorted((a, b) -> b.getLastUsedAt().compareTo(a.getLastUsedAt()))
                            .map(DeviceToken::getToken)
                            .findFirst()
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();

        // 6. Construir mensajes
        String subject = "Aviso: Puerta abierta al llegar un usuario";
        String html = """
            <h2>Incidencia detectada</h2>
            <p>El usuario <strong>%s</strong> ha llegado a la pista y ha encontrado la 
            puerta abierta.</p>
            <p>Se recomienda contactar con las ultimas personas que utilizaron las instalaciones.</p>
            """.formatted(user.getFullName());

        String pushTitle = "Puerta detectada abierta";
        String pushBody = "El usuario " + user.getFullName() + " encontró la puerta  abierta.";

        // 7. Enviar Email
        if (emailAyuntamiento != null && !emailAyuntamiento.isBlank()) {
            emailService.sendHtmlEmail(emailAyuntamiento, subject, html);
        } else {
        }

        // 8. Enviar Push a los administradores
        if (!latestTokens.isEmpty()) {
            for (String token : latestTokens) {
                notificationService.sendPush(token, pushTitle, pushBody);
            }
        } else {
        }

    }

    @Transactional
    public void deleteLock(Long lockId) {

        Lock lock = lockRepository.findById(lockId)
                .orElseThrow(() -> new RuntimeException("Cerradura no encontrada"));

        lockRepository.delete(lock);
    }




    public LockRegisterDTO toDTO(Lock lock) {
        LockRegisterDTO dto = new LockRegisterDTO();
        dto.setId(lock.getId());
        dto.setName(lock.getName());
        dto.setDeviceNumber(lock.getDeviceNumber());
        dto.setBleName(lock.getBleName());
        dto.setDeviceMAC(lock.getDeviceMAC());
        dto.setPistaId(lock.getPista().getId());
        return dto;
    }


}
