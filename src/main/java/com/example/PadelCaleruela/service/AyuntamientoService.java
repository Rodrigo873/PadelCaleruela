package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.AppProperties;
import com.example.PadelCaleruela.dto.*;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AyuntamientoService {

    private final AyuntamientoRepository repo;
    private final TarifaFranjaRepository franjaRepo;
    private final PistaRepository pistaRepo;
    private final TarifaRepository tarifaRepo;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final EmailService emailService;

    private final BlockRepository blockRepository;
    private final AppProperties appProperties; // contiene baseUrl



    public AyuntamientoService(AyuntamientoRepository repo, TarifaFranjaRepository franjaRepo,
                               PistaRepository pistaRepo, TarifaRepository tarifaRepo,
                               UserRepository userRepository, PasswordEncoder passwordEncoder,
                               EmailService emailService,BlockRepository blockRepository,AppProperties appProperties){
        this.repo=repo;
        this.franjaRepo=franjaRepo;
        this.pistaRepo=pistaRepo;
        this.tarifaRepo=tarifaRepo;
        this.userRepository=userRepository;
        this.passwordEncoder=passwordEncoder;
        this.emailService=emailService;
        this.appProperties=appProperties;
        this.blockRepository=blockRepository;
    }

    public Ayuntamiento findByCodigoPostal(String cp) {
        return repo.findByCodigoPostal(cp)
                .orElseThrow(() -> new RuntimeException("No existe ayuntamiento con ese código postal"));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    public AyuntamientoDTO findByCodigoPostalToDTO(String cp) {
        Ayuntamiento ayto=repo.findByCodigoPostal(cp)
                .orElseThrow(() -> new RuntimeException("No existe ayuntamiento con ese código postal"));
        AyuntamientoDTO ayuntamientoDTO=mapToDTO(ayto);
        return ayuntamientoDTO;
    }



    /** SOLO SUPERADMIN */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Transactional
    public Ayuntamiento crearAyuntamiento(AyuntamientoCreateRequest req,MultipartFile image) {

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
        a.setActivo(true);
        a.setPublico(req.getPublico());

        // 2.1️⃣ Subir imagen si viene
        if (image != null && !image.isEmpty()) {
            String imageUrl = null;
            try {
                imageUrl = saveAyuntamientoImage(image);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            a.setImageUrl(imageUrl);
        }

        Ayuntamiento saved = repo.save(a);

        // ---------------------------------------------------------
        // 3️⃣ Crear Pistas automáticamente
        // ---------------------------------------------------------
        for (int i = 1; i <= req.getNumeroPistas(); i++) {
            Pista p = new Pista();
            p.setAyuntamiento(saved);
            p.setNombre("Pista " + i);
            p.setActiva(true);

            // ⏰ Asignar horario desde el request
            p.setApertura(LocalTime.parse(req.getHoraApertura()));
            p.setCierre(LocalTime.parse(req.getHoraCierre()));

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
        admin.setUsername(saved.getNombre());       // login
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
        // 2.1️⃣ Subir imagen si viene
        if (image != null && !image.isEmpty()) {
            String imageUrl = null;
            try {
                imageUrl = saveAyuntamientoImage(image);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            admin.setProfileImageUrl(imageUrl);
        }
        userRepository.save(admin);

        // ---------------------------------------------------------
        // 7️⃣ Enviar email con las credenciales
        // ---------------------------------------------------------
        String html = """
            <html>
            <body>
                <h2>Bienvenido a BoostPlay 🎾</h2>
                <p>Se ha creado su ayuntamiento: <b>%s</b></p>
                <p>Estas son sus credenciales de acceso:</p>
                <ul>
                    <li><b>Correo:</b> %s</li>
                    <li><b>Contraseña:</b> %s</li>
                </ul>
                <p>Puede cambiar la contraseña desde la app.</p>
            </body>
            </html>
            """.formatted(
                saved.getNombre(),
                admin.getEmail(),
                rawPassword
        );

        emailService.sendHtmlEmail(
                saved.getEmail(),
                "Acceso administrador a BoosPlay",
                html
        );

        return saved;
    }


    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    @Transactional
    public ActualizarAytoYTarifaDTO actualizarAyuntamiento(Long id,
                                                           ActualizarAytoYTarifaDTO dto,
                                                           MultipartFile image) {

        com.example.PadelCaleruela.CustomUserDetails cud = (com.example.PadelCaleruela.CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        User currentUser = cud.getUser();


        // ⭐ SUPERADMIN → todo permitido
        if (currentUser.getRole().equals(Role.ADMIN)) {

            // ADMIN → solo puede actualizar SU ayuntamiento
            Long adminAytoId = currentUser.getAyuntamiento().getId();

            if (!adminAytoId.equals(id)) {
                throw new AccessDeniedException(
                        "No puedes modificar un ayuntamiento que no es el tuyo."
                );
            }
        }

        Ayuntamiento a = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        a.setNombre(dto.getNombre());
        a.setCodigoPostal(dto.getCodigoPostal());
        a.setNumeroPistas(dto.getNumeroPistas());
        a.setStripeAccountId(dto.getStripeAccountId());
        a.setTelefono(dto.getTelefono());
        a.setEmail(dto.getEmail());
        a.setPublico(dto.isPublico());

        // ⭐ Actualizar imagen si llega
        if (image != null && !image.isEmpty()) {
            try {
                String imageUrl = saveAyuntamientoImage(image);
                a.setImageUrl(imageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Error guardando imagen", e);
            }
        }

        repo.save(a);

        // ⭐ Actualizar tarifa base
        Tarifa tarifa = tarifaRepo.findByAyuntamientoId(id)
                .orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));

        if (dto.getPrecioBase() != null) {
            tarifa.setPrecioBase(dto.getPrecioBase());
            tarifaRepo.save(tarifa);
        }

        return mapToUpdteDTO(a, tarifa.getPrecioBase());
    }



    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public AyuntamientoDTO getAyuntamientoById(Long id) {

        com.example.PadelCaleruela.CustomUserDetails cud = (com.example.PadelCaleruela.CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        User currentUser = cud.getUser(); // tu getter


        Ayuntamiento ayto = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        // SUPERADMIN → puede ver cualquier ayuntamiento
        if (currentUser.getRole() == Role.SUPERADMIN) {
            return mapToDTO(ayto);
        }

        // ADMIN → solo si es su ayuntamiento
        if (currentUser.getRole() == Role.ADMIN &&
                !currentUser.getAyuntamiento().getId().equals(id)) {
            throw new SecurityException("No puedes ver este ayuntamiento");
        }

        return mapToDTO(ayto);
    }

    public List<AyuntamientoListDTO> getAyuntamientosSimple(User current) {


        return repo.findByActivoTrueAndPublicoTrue().stream()

                // 🔥 EXCLUIR AYUNTAMIENTOS QUE BLOQUEAN AL USUARIO
                .filter(a -> !blockRepository.existsByBlockedUserAndBlockedByAyuntamiento(current, a))

                .map(a -> new AyuntamientoListDTO(
                        a.getId(),
                        a.getNombre(),
                        a.getCodigoPostal(),
                        a.getImageUrl(),
                        true,
                        a.getNumeroPistas()
                ))
                .toList();
    }




    public List<AyuntamientoListDTO> listarAyuntamientos() {
        return repo.findAll().stream()
                .map(a -> {
                    AyuntamientoListDTO dto = new AyuntamientoListDTO();
                    dto.setId(a.getId());
                    dto.setNombre(a.getNombre());
                    dto.setCodigoPostal(a.getCodigoPostal());
                    dto.setImageUrl(a.getImageUrl());
                    dto.setActivo(a.isActivo());
                    if (a.getNumeroPistas()!=null) {
                        dto.setPistas(a.getNumeroPistas());
                    }else {
                        dto.setPistas(0);
                    }
                    return dto;
                })
                .toList();
    }


    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    @Transactional
    public TarifaFranjaDTO crearFranja(Long ayuntamientoId, TarifaFranjaDTO dto) {

        Ayuntamiento ayuntamiento = validatePermissions(ayuntamientoId);

        // Validar horas
        if (dto.getHoraFin() <= dto.getHoraInicio()) {
            throw new IllegalArgumentException("horaFin debe ser mayor que horaInicio");
        }

        // Validar solapamiento
        var existentes = franjaRepo.findByAyuntamientoId(ayuntamientoId);
        boolean solapa = existentes.stream().anyMatch(fr ->
                dto.getHoraInicio() < fr.getHoraFin() &&
                        dto.getHoraFin() > fr.getHoraInicio()
        );

        if (solapa)
            throw new IllegalArgumentException("La franja se solapa con otra existente");

        TarifaFranja fr = new TarifaFranja();
        fr.setAyuntamiento(ayuntamiento);
        updateEntityFromDTO(fr, dto);

        return toDTO(franjaRepo.save(fr));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public List<TarifaFranjaDTO> listarFranjas(Long ayuntamientoId) {

        validatePermissions(ayuntamientoId);

        return franjaRepo.findByAyuntamientoId(ayuntamientoId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    @Transactional
    public TarifaFranjaDTO actualizarFranja(Long ayuntamientoId, Long franjaId, TarifaFranjaDTO dto) {

        Ayuntamiento a = validatePermissions(ayuntamientoId);

        TarifaFranja franja = franjaRepo.findById(franjaId)
                .orElseThrow(() -> new RuntimeException("Franja no encontrada"));

        if (!franja.getAyuntamiento().getId().equals(ayuntamientoId)) {
            throw new SecurityException("La franja no pertenece a este ayuntamiento");
        }

        // Validar horas
        if (dto.getHoraFin() <= dto.getHoraInicio()) {
            throw new IllegalArgumentException("horaFin debe ser mayor que horaInicio");
        }

        // Comprobar solapamiento EXCLUYENDO la propia franja
        var existentes = franjaRepo.findByAyuntamientoId(ayuntamientoId)
                .stream()
                .filter(f -> !f.getId().equals(franjaId))
                .toList();

        boolean solapa = existentes.stream().anyMatch(fr ->
                dto.getHoraInicio() < fr.getHoraFin() &&
                        dto.getHoraFin() > fr.getHoraInicio()
        );

        if (solapa)
            throw new IllegalArgumentException("La franja se solapa con otra existente");

        updateEntityFromDTO(franja, dto);

        return toDTO(franjaRepo.save(franja));
    }


    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    @Transactional
    public void eliminarFranja(Long ayuntamientoId, Long franjaId) {

        validatePermissions(ayuntamientoId);

        TarifaFranja franja = franjaRepo.findById(franjaId)
                .orElseThrow(() -> new RuntimeException("Franja no encontrada"));

        if (!franja.getAyuntamiento().getId().equals(ayuntamientoId)) {
            throw new SecurityException("La franja no pertenece a este ayuntamiento");
        }

        franjaRepo.delete(franja);
    }


    private Ayuntamiento validatePermissions(Long ayuntamientoId) {
        User currentUser = (User) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        Ayuntamiento a = repo.findById(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        if (currentUser.getRole() == Role.ADMIN &&
                !currentUser.getAyuntamiento().getId().equals(ayuntamientoId)) {

            throw new SecurityException("No tienes permisos para modificar este ayuntamiento");
        }

        return a;
    }

    // Traer logo por ID de usuario
    public Map<String, String> getLogoByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null || user.getAyuntamiento() == null) {
            return Map.of("imageUrl", null);
        }

        return Map.of("imageUrl", user.getAyuntamiento().getImageUrl());
    }

    // Subir imagen física y asignarla al ayuntamiento
    public String uploadImage(Long id, MultipartFile file) throws IOException {
        Ayuntamiento ayto = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        String imageUrl = saveAyuntamientoImage(file);
        ayto.setImageUrl(imageUrl);

        repo.save(ayto);

        return imageUrl;
    }

    // MÉTODO QUE USA TU LÓGICA BASE
    private String saveAyuntamientoImage(MultipartFile file) throws IOException {

        Path uploadPath = Paths.get("uploads/ayuntamientos");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFile = file.getOriginalFilename();
        String sanitized = originalFile != null ? originalFile.replaceAll("\\s+", "_") : "unknown";

        String filename = UUID.randomUUID() + "_" + sanitized;
        Path filePath = uploadPath.resolve(filename);

        Files.write(filePath, file.getBytes());

        return appProperties.getBaseUrl() + "/uploads/ayuntamientos/" + filename;
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public TarifaDTO getTarifa(Long ayuntamientoId) {
        Tarifa tarifa = tarifaRepo.findByAyuntamientoId(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));

        return mapToDTO(tarifa);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public TarifaDTO crearTarifa(Long ayuntamientoId, TarifaDTO dto) {
        Ayuntamiento a = repo.findById(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        Tarifa t = new Tarifa();
        t.setAyuntamiento(a);
        t.setPrecioBase(new BigDecimal(dto.getPrecioBase()));

        tarifaRepo.save(t);
        return mapToDTO(t);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public TarifaDTO actualizarTarifa(Long ayuntamientoId, TarifaDTO dto) {
        Tarifa t = tarifaRepo.findByAyuntamientoId(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));

        t.setPrecioBase(new BigDecimal(dto.getPrecioBase()));
        tarifaRepo.save(t);

        return mapToDTO(t);
    }

    @Transactional
    public void moverUsuariosAlAyuntamientoNeutral(Long aytoId,String codigoPostal) {

        Ayuntamiento neutral = repo.findByCodigoPostal(codigoPostal)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento neutral no existe"));

        List<User> usuarios = userRepository.findByAyuntamientoId(aytoId);

        for (User u : usuarios) {
            u.setAyuntamiento(neutral);
            u.setRole(Role.USER);
        }

        userRepository.saveAll(usuarios);
    }

    @Transactional
    public void desactivarAyuntamiento(Long ayuntamientoId) {

        Ayuntamiento ay =repo.findById(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        ay.setActivo(false);
        repo.save(ay);

        moverUsuariosAlAyuntamientoNeutral(ayuntamientoId,"99999");
    }

    public void activarAyuntamiento(Long ayuntamientoId) {

        Ayuntamiento ay = repo.findById(ayuntamientoId)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado."));

        ay.setActivo(true);
        repo.save(ay);
    }

    public AyuntamientoBasicDTO getBasicInfo(Long aytoId) {

        Ayuntamiento ay = repo.findById(aytoId)
                .orElseThrow(() -> new RuntimeException("Ayuntamiento no encontrado"));

        return new AyuntamientoBasicDTO(ay.getId(), ay.getCodigoPostal());
    }

    private TarifaDTO mapToDTO(Tarifa t) {
        TarifaDTO dto = new TarifaDTO();
        dto.setId(t.getId());
        dto.setAyuntamientoId(t.getAyuntamiento().getId());
        dto.setPrecioBase(t.getPrecioBase().toString());
        return dto;
    }


    private TarifaFranjaDTO toDTO(TarifaFranja fr) {
        TarifaFranjaDTO dto = new TarifaFranjaDTO();
        dto.setId(fr.getId());
        dto.setHoraInicio(fr.getHoraInicio());
        dto.setHoraFin(fr.getHoraFin());
        dto.setPrecio(fr.getPrecio());
        return dto;
    }

    private AyuntamientoDTO mapToDTO(Ayuntamiento a) {
        AyuntamientoDTO dto = new AyuntamientoDTO();
        dto.setId(a.getId());
        dto.setNombre(a.getNombre());
        dto.setCodigoPostal(a.getCodigoPostal());
        dto.setNumeroPistas(a.getNumeroPistas());
        dto.setStripeAccountId(a.getStripeAccountId());
        dto.setTelefono(a.getTelefono());
        dto.setEmail(a.getEmail());
        dto.setImageUrl(a.getImageUrl());
        dto.setPublico(a.isPublico());
        return dto;
    }

    private ActualizarAytoYTarifaDTO mapToUpdteDTO(Ayuntamiento a,BigDecimal precioBase) {
        ActualizarAytoYTarifaDTO dto = new ActualizarAytoYTarifaDTO();
        dto.setId(a.getId());
        dto.setNombre(a.getNombre());
        dto.setCodigoPostal(a.getCodigoPostal());
        dto.setNumeroPistas(a.getNumeroPistas());
        dto.setStripeAccountId(a.getStripeAccountId());
        dto.setTelefono(a.getTelefono());
        dto.setEmail(a.getEmail());
        dto.setImageUrl(a.getImageUrl());
        dto.setPrecioBase(precioBase);
        return dto;
    }

    private void updateEntityFromDTO(TarifaFranja fr, TarifaFranjaDTO dto) {
        fr.setHoraInicio(dto.getHoraInicio());
        fr.setHoraFin(dto.getHoraFin());
        fr.setPrecio(dto.getPrecio());
    }

}
