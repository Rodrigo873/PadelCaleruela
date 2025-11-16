package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.AyuntamientoCreateRequest;
import com.example.PadelCaleruela.dto.TarifaFranjaDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;

@Service
public class AyuntamientoService {

    private AyuntamientoRepository repo;
    private TarifaFranjaRepository franjaRepo;
    private PistaRepository pistaRepo;
    private TarifaRepository tarifaRepo;

    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;

    private EmailService emailService;

    private ImageService imageService;


    public AyuntamientoService(AyuntamientoRepository repo, TarifaFranjaRepository franjaRepo,
                               PistaRepository pistaRepo, TarifaRepository tarifaRepo,
                               UserRepository userRepository, PasswordEncoder passwordEncoder,
                               EmailService emailService,ImageService imageService){
        this.repo=repo;
        this.franjaRepo=franjaRepo;
        this.pistaRepo=pistaRepo;
        this.tarifaRepo=tarifaRepo;
        this.userRepository=userRepository;
        this.passwordEncoder=passwordEncoder;
        this.emailService=emailService;
        this.imageService=imageService;
    }

    public Ayuntamiento findByCodigoPostal(String cp) {
        return repo.findByCodigoPostal(cp)
                .orElseThrow(() -> new RuntimeException("No existe ayuntamiento con ese código postal"));
    }

    /** SOLO SUPERADMIN */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Transactional
    public Ayuntamiento crearAyuntamiento(AyuntamientoCreateRequest req) {

        // ---------------------------------------------------------
        // 1️⃣ Validar duplicado de CP
        // ---------------------------------------------------------
        repo.findByCodigoPostal(req.getCodigoPostal()).ifPresent(a -> {
            throw new IllegalArgumentException("Ya existe un ayuntamiento con ese código postal");
        });

        // ---------------------------------------------------------
        // 2️⃣ Crear Ayuntamiento
        // ---------------------------------------------------------
        Ayuntamiento a = new Ayuntamiento();
        a.setNombre(req.getNombre());
        a.setCodigoPostal(req.getCodigoPostal());
        a.setNumeroPistas(req.getNumeroPistas());
        a.setStripeAccountId(req.getStripeAccountId());
        a.setTelefono(req.getTelefono());
        a.setEmail(req.getEmail());
        a.setImageUrl(imageService.buildUrl(a.getImageUrl()));
        Ayuntamiento saved = repo.save(a);

        // ---------------------------------------------------------
        // 3️⃣ Crear Pistas automáticamente
        // ---------------------------------------------------------
        for (int i = 1; i <= req.getNumeroPistas(); i++) {
            Pista p = new Pista();
            p.setAyuntamiento(saved);
            p.setNombre("Pista " + i);
            p.setActiva(true);
            pistaRepo.save(p);
        }

        // ---------------------------------------------------------
        // 4️⃣ Crear Tarifa base
        // ---------------------------------------------------------
        Tarifa tarifa = new Tarifa();
        tarifa.setAyuntamiento(saved);
        tarifa.setPrecioBase(req.getPrecioBase());
        tarifaRepo.save(tarifa);

        // ---------------------------------------------------------
        // 5️⃣ Crear Tarifas Franja (si llegan)
        // ---------------------------------------------------------
        if (req.getFranjas() != null) {
            for (TarifaFranjaDTO f : req.getFranjas()) {

                if (f.getHoraFin() <= f.getHoraInicio()) {
                    throw new IllegalArgumentException("La franja horaria debe tener horaFin > horaInicio");
                }

                TarifaFranja fr = new TarifaFranja();
                fr.setAyuntamiento(saved);
                fr.setHoraInicio(f.getHoraInicio());
                fr.setHoraFin(f.getHoraFin());
                fr.setPrecio(f.getPrecio());
                franjaRepo.save(fr);
            }
        }

        // ---------------------------------------------------------
        // 6️⃣ Crear un usuario ADMIN para el ayuntamiento
        // ---------------------------------------------------------
        User admin = new User();
        admin.setUsername(saved.getEmail());       // login
        admin.setEmail(saved.getEmail());
        admin.setFullName("Admin - " + saved.getNombre());
        admin.setRole(Role.ADMIN);
        admin.setAyuntamiento(saved);
        admin.setStatus(UserStatus.OFFLINE);

        // Contraseña aleatoria
        final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        String rawPassword = sb.toString();

        admin.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(admin);

        // ---------------------------------------------------------
        // 7️⃣ Enviar email con las credenciales
        // ---------------------------------------------------------
        String html = """
            <html>
            <body>
                <h2>Bienvenido a PadelApp 🎾</h2>
                <p>Se ha creado su ayuntamiento: <b>%s</b></p>
                <p>Estas son sus credenciales de acceso:</p>
                <ul>
                    <li><b>Usuario:</b> %s</li>
                    <li><b>Contraseña:</b> %s</li>
                </ul>
                <p>Puede cambiar la contraseña desde la app.</p>
            </body>
            </html>
            """.formatted(
                saved.getNombre(),
                admin.getUsername(),
                rawPassword
        );

        emailService.sendHtmlEmail(
                saved.getEmail(),
                "Acceso administrador a PadelApp",
                html
        );

        return saved;
    }


}
