package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.AppProperties;
import com.example.PadelCaleruela.dto.PostDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AppProperties appProperties;

    private final UserSeenPostRepository userSeenPostRepository;

    private final BlockRepository blockRepository;
    private final LikeRepository likeRepository;
    private  final AuthService authService;

    public PostService(PostRepository repo,
                       UserRepository userRepository,
                       UserService userService,
                       AppProperties appProperties,
                       UserSeenPostRepository userSeenPostRepository,
                       BlockRepository blockRepository,
                       LikeRepository likeRepository,
                       AuthService authService
    ) {
        this.postRepository = repo;
        this.userRepository = userRepository;
        this.userService = userService;
        this.appProperties=appProperties;
        this.userSeenPostRepository=userSeenPostRepository;
        this.blockRepository=blockRepository;
        this.likeRepository=likeRepository;
        this.authService=authService;
    }

    // 🔹 Feed personalizado para el usuario autenticado
    //    - Posts de usuarios que sigue (PUBLIC + FRIENDS)
    //    - Sus propios posts (incluye PRIVATE)
    //    - Posts PUBLIC de su ayuntamiento
    public List<PostDTO> getFeed(User currentUser) {

        Long userId = currentUser.getId();
        LocalDateTime minDate = LocalDateTime.now().minusHours(24);

        // Posts de personas que sigo
        List<Post> posts = postRepository.findRecentFeedForUser(userId, minDate);

        // Mis propios posts (solo FRIENDS o PUBLIC)
        List<Post> misPosts = postRepository
                .findByUserIdAndCreatedAtGreaterThanEqual(userId, minDate)
                .stream()
                .filter(p ->p.getVisibility() == Visibility.FRIENDS)
                .toList();

        // Mezclamos feed + mis posts sin duplicados
        List<Post> combinados = Stream.concat(posts.stream(), misPosts.stream())
                .distinct()
                .toList();

        return combinados.stream()
                .map(this::convertToDTO)
                .toList();
    }







    // 🔹 Feed de otro usuario (solo para SuperAdmin)
    public List<PostDTO> getFeedForUserId(Long targetUserId, User currentUser) {
        if (!isSuperAdmin(currentUser)) {
            throw new AccessDeniedException("Solo SuperAdmin puede ver el feed de otros usuarios.");
        }
        List<Post> posts = postRepository.findFeedForUser(targetUserId);
        return posts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 🔹 Feed público: posts PUBLIC del ayuntamiento del usuario
    public List<PostDTO> getPublicFeed(User currentUser) {

        Ayuntamiento ayto = currentUser.getAyuntamiento();
        Long userId = currentUser.getId();

        if (ayto == null) return List.of();

        Long ayId = ayto.getId();
        LocalDateTime minDate = LocalDateTime.now().minusHours(24);

        // 1️⃣ Traemos tus posts (siempre)
        List<Post> misPosts = postRepository
                .findByUserIdAndCreatedAtGreaterThanEqual(userId, minDate)
                .stream()
                .filter(p -> p.getVisibility() == Visibility.PUBLIC)
                .toList();

        // 2️⃣ Traemos los demás posts públicos no seguidos
        List<Post> otros = postRepository.findRecentPublicPostsNotFollowed(
                userId, ayId, minDate
        );

        // 3️⃣ Unimos ambas listas
        List<Post> posts = Stream.concat(misPosts.stream(), otros.stream())
                .distinct()
                .toList();

        // === Bloqueos ===
        Set<Long> yoBloqueo = blockRepository.findByBlockedByUser(currentUser)
                .stream().map(b -> b.getBlockedUser().getId()).collect(Collectors.toSet());

        Set<Long> meBloquearon = blockRepository.findByBlockedUser(currentUser)
                .stream().filter(b -> b.getBlockedByUser() != null)
                .map(b -> b.getBlockedByUser().getId()).collect(Collectors.toSet());

        Set<Long> aytoBloquea = blockRepository.findByBlockedByAyuntamiento(ayto)
                .stream().map(b -> b.getBlockedUser().getId()).collect(Collectors.toSet());

        Set<Long> aytosQueMeBloquearon = blockRepository.findByBlockedUser(currentUser)
                .stream().filter(b -> b.getBlockedByAyuntamiento() != null)
                .map(b -> b.getBlockedByAyuntamiento().getId()).collect(Collectors.toSet());

        // === Filtrado final ===
        return posts.stream()
                .filter(p -> p.getVisibility() == Visibility.PUBLIC ||
                        p.getUser().getId().equals(userId)) // 🔥 Tus posts siempre entran
                .filter(p -> !yoBloqueo.contains(p.getUser().getId()))
                .filter(p -> !meBloquearon.contains(p.getUser().getId()))
                .filter(p -> !aytoBloquea.contains(p.getUser().getId()))
                .filter(p -> !aytosQueMeBloquearon.contains(ayId))
                .map(this::convertToDTO)
                .toList();
    }





    /** 👉 Registrar que un post fue visto */
    public void markAsSeen(Long userId, Long postId) {
        if (!userSeenPostRepository.existsByUserIdAndPostId(userId, postId)) {
            UserSeenPost seen = new UserSeenPost();
            seen.setUserId(userId);
            seen.setPostId(postId);
            seen.setSeenAt(LocalDateTime.now());
            userSeenPostRepository.save(seen);
        }
    }

    // 🔹 Posts de un usuario concreto, respetando visibilidad
    public List<PostDTO> getPostsByUser(Long ownerId, User currentUser) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        List<Post> posts = postRepository.findByUserId(ownerId);

        return posts.stream()
                .filter(p -> canUserViewPostPerfil(currentUser, p))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }


    private boolean canUserViewPostPerfil(User viewer, Post post) {
        if (viewer == null || post == null) return false;

        User owner = post.getUser();

        // 🔥 SUPERADMIN ve todo
        if (isSuperAdmin(viewer)) {
            return true;
        }

        // 🔥 Siempre puedes ver tus propios posts (incluye PRIVATE)
        if (viewer.getId().equals(owner.getId())) {
            return true;
        }

        Visibility visibility = post.getVisibility();
        boolean followsOwner = userService.isFollowing(viewer.getId(), owner.getId());

        switch (visibility) {

            case PRIVATE:
                // ❌ Nadie puede ver posts privados de otro
                return false;

            case FRIENDS:
                // 🔥 Solo si lo sigo
                return followsOwner;

            case PUBLIC:
                // 🔥 Todos pueden verlo
                return true;
        }

        return false;
    }


    // 🔹 Obtener un post por id, respetando visibilidad
    public PostDTO getPostById(Long postId, User currentUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado."));

        if (!canUserViewPostPerfil(currentUser, post)) {
            throw new AccessDeniedException("No tienes permiso para ver este post.");
        }

        return convertToDTO(post);
    }


    // 🔹 Crear post
    @Transactional
    public PostDTO createPost(PostDTO postDTO, User currentUser, MultipartFile image) {

        User owner = currentUser;

        // 🔐 SuperAdmin puede publicar como otro usuario
        if (isSuperAdmin(currentUser)
                && postDTO.getUserId() != null
                && !postDTO.getUserId().equals(currentUser.getId())) {

            owner = userRepository.findById(postDTO.getUserId())
                    .orElseThrow(() -> new RuntimeException("Usuario destino no encontrado."));
        }

        // ❌ Admin NO puede publicar en nombre de otros
        if (isAdmin(currentUser) &&
                !postDTO.getUserId().equals(currentUser.getId())) {
            throw new SecurityException("Los administradores no pueden crear posts en nombre de otros usuarios.");
        }

        // ❌ Usuario normal debe ser él mismo
        if (!isSuperAdmin(currentUser) &&
                !postDTO.getUserId().equals(currentUser.getId())) {
            throw new SecurityException("No tienes permisos para crear posts para otros usuarios.");
        }

        Post post = new Post();
        post.setUser(owner);
        post.setMessage(postDTO.getMessage());
        post.setMatchResult(postDTO.getMatchResult());
        post.setVisibility(postDTO.getVisibility() != null ? postDTO.getVisibility() : Visibility.PUBLIC);
        post.setCreatedAt(LocalDateTime.now());

        // Multitenant: el post pertenece al ayuntamiento del propietario
        post.setAyuntamiento(owner.getAyuntamiento());

        // 📸 Si viene imagen --> guardarla
        if (image != null && !image.isEmpty()) {
            String imageUrl;
            try {
                imageUrl = savePostImage(image);
            } catch (IOException e) {
                throw new RuntimeException("Error guardando imagen del post", e);
            }
            post.setImageUrl(imageUrl);
        }

        Post saved = postRepository.save(post);
        return convertToDTO(saved);
    }

    private String savePostImage(MultipartFile file) throws IOException {

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


    // 🔹 Actualizar post
    public PostDTO updatePost(Long postId, PostDTO postDTO, User currentUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado."));

        if (!canEditOrDelete(currentUser, post)) {
            throw new AccessDeniedException("No tienes permiso para editar este post.");
        }

        // Solo mensaje, resultado y visibilidad; no se cambia el usuario ni ayuntamiento
        post.setMessage(postDTO.getMessage());
        post.setMatchResult(postDTO.getMatchResult());
        if (postDTO.getVisibility() != null) {
            post.setVisibility(postDTO.getVisibility());
        }

        Post updated = postRepository.save(post);
        return convertToDTO(updated);
    }

    // 🔹 Eliminar post
    public void deletePost(Long postId, User currentUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado."));

        if (!canEditOrDelete(currentUser, post)) {
            throw new AccessDeniedException("No tienes permiso para eliminar este post.");
        }

        postRepository.delete(post);
    }

    @Transactional
    public PostDTO updatePostVisibility(Long postId, Visibility visibility, User currentUser) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado."));

        // 🔒 Solo el dueño del post o SuperAdmin pueden cambiar visibilidad
        if (!currentUser.getId().equals(post.getUser().getId()) && !isSuperAdmin(currentUser)) {
            throw new AccessDeniedException("No tienes permiso para modificar la visibilidad de este post.");
        }

        // Actualizamos únicamente la visibilidad
        post.setVisibility(visibility);

        // Guardamos y devolvemos DTO
        Post saved = postRepository.save(post);
        return convertToDTO(saved);
    }


    // 🔹 Conversión entidad → DTO
    private PostDTO convertToDTO(Post post) {
        PostDTO dto = new PostDTO();

        dto.setId(post.getId());
        dto.setUserId(post.getUser().getId());
        dto.setMessage(post.getMessage());
        dto.setMatchResult(post.getMatchResult());
        dto.setVisibility(post.getVisibility());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setImageUrl(post.getImageUrl());
        dto.setUsername(post.getUser().getUsername());
        dto.setUserImageUrl(post.getUser().getProfileImageUrl());

        // 🔥 Añadimos likes
        long likes = likeRepository.countByPost(post);
        dto.setLikesCount(likes);

        User current = authService.getCurrentUser();
        boolean liked = likeRepository.existsByUserAndPost(current, post);
        dto.setLikedByCurrentUser(liked);

        return dto;
    }



    // ==========================================
    // 🔐 LÓGICA DE SEGURIDAD / VISIBILIDAD
    // ==========================================

    // Qué puede ver un usuario
    private boolean canUserViewPost(User viewer, Post post) {
        if (viewer == null) return false;

        // SuperAdmin lo ve todo
        if (isSuperAdmin(viewer)) return true;

        Long viewerId = viewer.getId();
        Long ownerId = post.getUser().getId();

        // Siempre puedes ver tus propios posts (incluye PRIVATE)
        if (viewerId.equals(ownerId)) {
            return true;
        }

        Visibility visibility = post.getVisibility();

        // PRIVATE: solo el owner (SuperAdmin ya lo hemos controlado)
        if (visibility == Visibility.PRIVATE) {
            return false;
        }

        // ¿Sigue el viewer al dueño del post?
        boolean following = userService.isFollowing(viewerId, ownerId);

        // FRIENDS: solo si sigues al usuario
        if (visibility == Visibility.FRIENDS) {
            return following;
        }

        // PUBLIC:
        //  - Si sigues al usuario => lo ves
        //  - Si no lo sigues => solo si es del mismo ayuntamiento
        if (visibility == Visibility.PUBLIC) {
            if (following) return true;

            Ayuntamiento viewerAyto = viewer.getAyuntamiento();
            Ayuntamiento postAyto = post.getAyuntamiento();

            return viewerAyto != null && postAyto != null
                    && viewerAyto.getId().equals(postAyto.getId());
        }

        // Por si aparece algún valor raro
        return false;
    }

    // Quién puede editar/eliminar posts
    private boolean canEditOrDelete(User actor, Post post) {
        if (actor == null) return false;

        // SuperAdmin puede hacer TODO
        if (isSuperAdmin(actor)) {
            return true;
        }

        // Admin NUNCA puede editar ni eliminar (ni siquiera los suyos)
        if (isAdmin(actor)) {
            return false;
        }

        // Usuario normal: solo si es el dueño
        return actor.getId().equals(post.getUser().getId());
    }

    private boolean isSuperAdmin(User user) {
        return hasRole(user, Role.SUPERADMIN);
    }

    private boolean isAdmin(User user) {
        return hasRole(user, Role.ADMIN);
    }


    private boolean hasRole(User user, Role role) {
        return user.getRole() == role;
    }


}
