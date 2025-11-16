package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.AppProperties;
import com.example.PadelCaleruela.dto.LeagueDTO;
import com.example.PadelCaleruela.dto.LeaguePairDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;

    private final LeagueInvitationRepository leagueInvitationRepository;

    private final LeagueMatchRepository leagueMatchRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final LeagueRankingRepository leagueRankingRepository;
    private final LeagueTeamRankingRepository leagueTeamRankingRepository;

    private final AuthService authService;

    private final AppProperties appProperties;

    private final FileStorageService fileStorageService;


    public LeagueService(
            LeagueRepository leagueRepository,
            UserRepository userRepository,
            LeagueInvitationRepository leagueInvitationRepository,
            LeagueMatchRepository matchRepository,
            LeagueTeamRepository leagueTeamRepository,
            LeagueTeamRankingRepository leagueTeamRankingRepository,
            LeagueRankingRepository leagueRankingRepository,
            AuthService authService,
            AppProperties appProperties,
            FileStorageService fileStorageService
    ) {
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.leagueInvitationRepository = leagueInvitationRepository;
        this.leagueMatchRepository = matchRepository;
        this.leagueTeamRepository = leagueTeamRepository;
        this.leagueTeamRankingRepository = leagueTeamRankingRepository;
        this.leagueRankingRepository = leagueRankingRepository;
        this.authService = authService;
        this.appProperties = appProperties;
        this.fileStorageService=fileStorageService;
    }


    @Transactional
    public LeagueDTO createLeague(LeagueDTO dto,MultipartFile image) throws IOException {

        User creator = userRepository.findById(dto.getCreatorId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User current = authService.getCurrentUser();

        // USER → solo crear su propia liga
        if (authService.isUser() && !current.getId().equals(dto.getCreatorId())) {
            throw new AccessDeniedException("No puedes crear ligas para otro usuario.");
        }

        // ADMIN → solo si pertenece al mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(creator);
        }


        League league = new League();
        league.setName(dto.getName());
        league.setDescription(dto.getDescription());
        league.setIsPublic(dto.getIsPublic());
        if (image != null && !image.isEmpty()) {
            String imageUrl = saveProfileImage(image);
            league.setImageUrl(imageUrl);
        }
        league.setRegistrationDeadline(dto.getRegistrationDeadline());
        league.setStartDate(dto.getStartDate());
        league.setEndDate(dto.getEndDate());
        league.setStatus(LeagueStatus.PENDING);

        league.setCreator(creator);
        league.setAyuntamiento(creator.getAyuntamiento());  // 🔥 MULTI-AYTO

        leagueRepository.save(league);

        return mapToDto(league);
    }

    private String saveProfileImage(MultipartFile file) throws IOException {

        // Crear carpeta si no existe
        Path uploadPath = Paths.get("uploads/profile-images");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Sanear nombre
        String originalFileName = file.getOriginalFilename();
        String sanitizedFileName = originalFileName != null
                ? originalFileName.replaceAll("\\s+", "_")
                : "unknown";

        // Nombre único
        String filename = UUID.randomUUID() + "_" + sanitizedFileName;
        Path filePath = uploadPath.resolve(filename);

        // Guardar archivo físico
        Files.write(filePath, file.getBytes());

        // URL pública completa
        return appProperties.getBaseUrl() + "/uploads/profile-images/" + filename;
    }

    /**
     * 🔹 Devuelve todas las ligas en las que participa un jugador.
     */
    @Transactional(readOnly = true)
    public List<LeagueDTO> getLeaguesByPlayer(Long playerId) {

        User current = authService.getCurrentUser();
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // USER → solo ver sus propias ligas
        if (authService.isUser() && !current.getId().equals(playerId)) {
            throw new AccessDeniedException("No puedes ver las ligas de otro usuario.");
        }

        // ADMIN → solo si es mismo ayuntamiento
        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(player);
        }

        return leagueRepository.findAll().stream()
                .filter(l -> Objects.equals(l.getAyuntamiento().getId(), player.getAyuntamiento().getId())
                        || authService.isSuperAdmin())
                .filter(l -> l.getStatus() != LeagueStatus.FINISHED)
                .filter(l -> l.getCreator().getId().equals(playerId) ||
                        l.getPlayers().stream().anyMatch(p -> p.getId().equals(playerId)))
                .map(this::mapToDto)
                .toList();
    }



    @Transactional(readOnly = true)
    public List<LeagueDTO> getFinishedLeaguesByPlayer(Long playerId) {

        User current = authService.getCurrentUser();
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (authService.isUser() && !current.getId().equals(playerId)) {
            throw new AccessDeniedException("No puedes ver las ligas de otro usuario.");
        }

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(player);
        }

        return leagueRepository.findAll().stream()
                .filter(l -> l.getStatus() == LeagueStatus.FINISHED)
                .filter(l -> authService.isSuperAdmin()
                        || Objects.equals(l.getAyuntamiento().getId(), player.getAyuntamiento().getId()))
                .filter(l -> l.getCreator().getId().equals(playerId)
                        || l.getPlayers().stream().anyMatch(p -> p.getId().equals(playerId)))
                .map(this::mapToDto)
                .toList();
    }


    /** 🔥 Retorna todas las ligas con estado "ACTIVE" */
    public List<LeagueDTO> getActiveLeagues() {

        User current = authService.getCurrentUser();

        List<League> leagues;

        if (authService.isSuperAdmin()) {
            leagues = leagueRepository.findAllActivePublicLeagues();
        } else {
            leagues = leagueRepository.findAllActivePublicLeaguesByAyuntamiento(
                    current.getAyuntamiento().getId()
            );
        }

        return leagues.stream()
                .map(this::mapToDto)
                .toList();
    }



    // 🆕 Obtener jugadores agrupados por parejas
    @Transactional(readOnly = true)
    public List<LeaguePairDTO> getLeagueParticipantsGrouped(Long leagueId) {

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("Liga no encontrada"));

        // 🔐 Multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
        }

        List<LeagueTeam> teams = leagueTeamRepository.findByLeagueIdWithPlayers(leagueId);

        List<LeaguePairDTO> pairs = new ArrayList<>();

        for (LeagueTeam team : teams) {
            List<User> players = new ArrayList<>(team.getPlayers());

            List<Long> playerIds = players.stream().map(User::getId).toList();
            List<String> usernames = players.stream().map(User::getUsername).toList();
            List<String> images = players.stream()
                    .map(u -> u.getProfileImageUrl() != null
                            ? u.getProfileImageUrl()
                            : "https://ui-avatars.com/api/?name=" + u.getUsername())
                    .toList();

            pairs.add(new LeaguePairDTO(playerIds, usernames, images));
        }

        // Jugadores sin pareja
        Set<Long> playersInTeams = teams.stream()
                .flatMap(t -> t.getPlayers().stream().map(User::getId))
                .collect(Collectors.toSet());

        List<User> unpairedPlayers = league.getPlayers().stream()
                .filter(p -> !playersInTeams.contains(p.getId()))
                .toList();

        for (User p : unpairedPlayers) {
            pairs.add(new LeaguePairDTO(
                    List.of(p.getId()),
                    List.of(p.getUsername()),
                    List.of(p.getProfileImageUrl() != null
                            ? p.getProfileImageUrl()
                            : "https://ui-avatars.com/api/?name=" + p.getUsername())
            ));
        }

        return pairs;
    }



    public List<LeagueDTO> getAllPublicLeagues() {

        User current = authService.getCurrentUser();

        List<League> leagues;

        if (authService.isSuperAdmin()) {
            leagues = leagueRepository.findAllPublicPendingLeagues();
        } else {
            leagues = leagueRepository.findAllPublicPendingLeaguesByAyuntamiento(
                    current.getAyuntamiento().getId()
            );
        }

        return leagues.stream()
                .map(this::mapToDto)
                .toList();
    }


    @Transactional(readOnly = true)
    public boolean isUserInLeague(Long leagueId, Long userId) {

        User current = authService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // USER → solo preguntarse a sí mismo
        if (authService.isUser() && !current.getId().equals(userId)) {
            throw new AccessDeniedException("No puedes comprobar ligas de otro usuario.");
        }

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("Liga no encontrada"));

        // ADMIN → mismo ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            authService.ensureSameAyuntamiento(target);
        }

        return league.getPlayers().stream()
                .anyMatch(u -> u.getId().equals(userId));
    }



    public LeagueDTO getLeague(Long id) {

        League league = leagueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("League not found"));

        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());

            boolean isCreator = league.getCreator() != null &&
                    league.getCreator().getId().equals(current.getId());

            boolean isPlayer = league.getPlayers().stream()
                    .anyMatch(p -> p.getId().equals(current.getId()));

            // Si no es pública y no participa → fuera
            if (!Boolean.TRUE.equals(league.getIsPublic()) &&
                    !isCreator &&
                    !isPlayer &&
                    !authService.isAdmin()) {
                throw new AccessDeniedException("No puedes acceder a esta liga privada.");
            }
        }

        return mapToDto(league);
    }


    @Transactional
    public void addPlayerToLeague(Long leagueId, Long playerId) {

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User current = authService.getCurrentUser();

        // Multi-ayuntamiento
        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            authService.ensureSameAyuntamiento(player);
        }


        // Optional: bloquear si la liga está cerrada
        if (league.getStatus() == LeagueStatus.FINISHED) {
            throw new RuntimeException("La liga ya está finalizada.");
        }

        // Invitación pendiente → marcar como ACCEPTED
        leagueInvitationRepository.findByLeague_IdAndReceiver_IdAndStatus(
                        leagueId, playerId, InvitationStatus.PENDING)
                .ifPresent(inv -> {
                    inv.setStatus(InvitationStatus.ACCEPTED);
                    leagueInvitationRepository.save(inv);
                });

        boolean alreadyInLeague = league.getPlayers().stream()
                .anyMatch(p -> p.getId().equals(playerId));

        if (!alreadyInLeague) {
            league.addPlayer(player);
            leagueRepository.save(league);
        }
    }

    private String saveLeagueImage(MultipartFile file) throws IOException {

        Path uploadPath = Paths.get("uploads/league-profile");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFileName = file.getOriginalFilename();
        String sanitizedFileName = originalFileName != null
                ? originalFileName.replaceAll("\\s+", "_")
                : "unknown";

        String filename = UUID.randomUUID() + "_" + sanitizedFileName;

        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, file.getBytes());

        return appProperties.getBaseUrl() + "/uploads/league-profile/" + filename;
    }



    @Transactional
    public void removePlayerFromLeague(Long leagueId, Long playerId) {

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User current = authService.getCurrentUser();

        if (!authService.isSuperAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            authService.ensureSameAyuntamiento(player);
        }

        // USER → solo puede borrarse a sí mismo
        if (authService.isUser() && !current.getId().equals(playerId)) {
            throw new AccessDeniedException("No puedes eliminar a otro jugador de la liga.");
        }

        league.removePlayer(player);
    }


    @Transactional
    public boolean deleteLeague(Long leagueId, Long userId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // 1️⃣ Borrar dependencias directas antes de la liga
        leagueInvitationRepository.deleteAllByLeague(league);
        leagueMatchRepository.deleteAllByLeague(league);
        leagueTeamRankingRepository.deleteAllByLeague(league);
        leagueRankingRepository.deleteAllByLeague(league);
        leagueTeamRepository.deleteAllByLeague(league);

        // 2️⃣ Borrar relaciones many-to-many (jugadores en liga)
        league.getPlayers().clear();
        leagueRepository.save(league); // sincroniza el cambio

        // ✅ Verificar que el usuario es el creador
        if (league.getCreator() != null && league.getCreator().getId().equals(userId)) {
            leagueRepository.delete(league);
            return true;
        }

        // ❌ No es el creador
        return false;
    }

    private LeagueDTO mapToDto(League league) {
        LeagueDTO dto = new LeagueDTO();
        dto.setId(league.getId());
        dto.setName(league.getName());
        dto.setDescription(league.getDescription());
        dto.setIsPublic(league.getIsPublic());
        String img = league.getImageUrl();
        if (img != null && !img.isBlank()) {
            if (!img.startsWith("http")) {
                img = appProperties.getBaseUrl() + img;
            }
        }
        dto.setImageUrl(img);
        dto.setRegistrationDeadline(league.getRegistrationDeadline());
        dto.setStartDate(league.getStartDate());
        dto.setEndDate(league.getEndDate());
        dto.setStatus(league.getStatus().name());
        dto.setCreatorId(league.getCreator() != null ? league.getCreator().getId() : null);
        dto.setPlayerIds(league.getPlayers().stream().map(User::getId).collect(Collectors.toSet()));
        return dto;
    }
}

