package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.WelockClient;
import com.example.PadelCaleruela.dto.RegisterRequest;
import com.example.PadelCaleruela.dto.UnlockRequest;
import com.example.PadelCaleruela.model.Lock;
import com.example.PadelCaleruela.model.Reservation;
import com.example.PadelCaleruela.service.LockService;
import com.example.PadelCaleruela.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/welock")
public class WelockController {

    private final WelockClient welock;
    private final ReservationService reservaService;

    private final LockService lockService;


    public WelockController(WelockClient welock,ReservationService reservaService,LockService lockService) {
        this.welock = welock;
        this.reservaService=reservaService;
        this.lockService=lockService;
    }

    @PostMapping("/register-device")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        welock.registerDevice(req.getDeviceNumber(), req.getDeviceName(), req.getUserId());
        return ResponseEntity.ok(Map.of("status", "registered"));
    }

    @PostMapping("/unlock-command")
    public ResponseEntity<?> unlock(@RequestBody UnlockRequest req) {

        System.out.println("========== 🟦 UNLOCK REQUEST RECIBIDO =================");
        System.out.println("ReservationId: " + req.getReservationId());
        System.out.println("UserId: " + req.getUserId());
        System.out.println("DeviceNumber (req): " + req.getDeviceNumber());
        System.out.println("BleName (req): " + req.getBleName());
        System.out.println("Power (req): " + req.getPower());
        System.out.println("RandomFactor (req): " + req.getRandomFactor());
        System.out.println("========================================================");

        // 1️⃣ Buscar reserva
        Reservation reserva = reservaService.findById(req.getReservationId())
                .orElseThrow(() -> {
                    System.out.println("❌ ERROR: Reserva no encontrada");
                    return new RuntimeException("Reserva no encontrada");
                });

        System.out.println("✔ Reserva cargada correctamente");
        System.out.println("Reserva -> start: " + reserva.getStartTime() + " | end: " + reserva.getEndTime());
        System.out.println("Reserva -> pistaId: " + reserva.getPista().getId());
        System.out.println("Reserva -> creador: " + reserva.getUser().getId());

        Long userId = req.getUserId();

        // 2️⃣ Validar si pertenece a la reserva
        boolean esCreador = reserva.getUser().getId().equals(userId);
        boolean esJugador = reserva.getJugadores().stream()
                .peek(j -> System.out.println("Jugador en reserva: " + j.getId()))
                .anyMatch(u -> u.getId().equals(userId));

        System.out.println("¿Es creador? " + esCreador);
        System.out.println("¿Es jugador? " + esJugador);

        if (!esCreador && !esJugador) {
            System.out.println("❌ Usuario NO pertenece a la reserva");
            return ResponseEntity.status(403).body(Map.of(
                    "autorizado", false,
                    "error", "No formas parte de esta reserva"
            ));
        }

        // 3️⃣ Ventana de tiempo permitida
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime inicio = reserva.getStartTime().minusMinutes(5);
        LocalDateTime fin = reserva.getEndTime().plusMinutes(5);

        System.out.println("Ahora: " + ahora);
        System.out.println("Ventana inicio: " + inicio);
        System.out.println("Ventana fin: " + fin);

        if (ahora.isBefore(inicio) || ahora.isAfter(fin)) {
            System.out.println("❌ Fuera del horario permitido");
            return ResponseEntity.status(403).body(Map.of(
                    "autorizado", false,
                    "error", "Fuera del horario permitido"
            ));
        }

        // 4️⃣ Obtener cerradura vinculada a la pista
        System.out.println("Buscando cerradura para pista: " + reserva.getPista().getId());

        Lock lock = lockService.findByPistaId(reserva.getPista().getId())
                .orElseThrow(() -> {
                    System.out.println("❌ ERROR: No existe cerradura para esta pista");
                    return new RuntimeException("La pista no tiene cerradura asignada");
                });

        System.out.println("✔ Cerradura encontrada:");
        System.out.println("DB deviceNumber: " + lock.getDeviceNumber());
        System.out.println("DB bleName: " + lock.getBleName());

        // 5️⃣ Validación de datos
        if (!lock.getDeviceNumber().equals(req.getDeviceNumber()) ||
                !lock.getBleName().equals(req.getBleName())) {

            System.out.println("❌ Error: Datos de cerradura incorrectos");
            System.out.println("Comparación ->");
            System.out.println("req.deviceNumber = " + req.getDeviceNumber() + " | db = " + lock.getDeviceNumber());
            System.out.println("req.bleName      = " + req.getBleName() + " | db = " + lock.getBleName());

            return ResponseEntity.status(403).body(Map.of(
                    "autorizado", false,
                    "error", "Datos de cerradura incorrectos"
            ));
        }

        // 6️⃣ Llamar API WeLock
        System.out.println("✔ Validación correcta, pidiendo comando a WELOCK...");

        String cmd = welock.getUnlockCommand(
                req.getDeviceNumber(),
                req.getBleName(),
                req.getPower(),
                req.getRandomFactor()
        );

        System.out.println("✔ Comando recibido: " + cmd);
        System.out.println("========== 🟩 FIN UNLOCK REQUEST =================");

        return ResponseEntity.ok(Map.of(
                "autorizado", true,
                "command", cmd
        ));
    }

    @PostMapping("/sync-time")
    public ResponseEntity<?> syncTime(@RequestBody Map<String, Object> req) {

        String deviceNumber = req.get("deviceNumber").toString();
        String bleName      = req.get("bleName").toString();

        long timestamp = Long.parseLong(req.get("timestamp").toString());

        // normal: "0000"
        String randomFactor = req.get("randomFactor").toString();

        System.out.println("========== 🟦 SYNC TIME REQUEST =================");
        System.out.println("DeviceNumber: " + deviceNumber);
        System.out.println("BleName: " + bleName);
        System.out.println("Timestamp: " + timestamp);
        System.out.println("RandomFactor: " + randomFactor);
        System.out.println("=================================================");

        String cmd = welock.getSyncTimeCommand(
                deviceNumber,
                bleName,
                timestamp,
                randomFactor
        );

        System.out.println("🟢 Comando SyncTime recibido: " + cmd);

        return ResponseEntity.ok(Map.of(
                "command", cmd
        ));
    }



    @GetMapping("/puede-abrir/{id}")
    public Map<String, Boolean> puedeAbrir(@PathVariable Long id) {

        boolean autorizado = reservaService.usuarioPuedeAbrir(id);

        return Map.of("autorizado", autorizado);
    }
}

