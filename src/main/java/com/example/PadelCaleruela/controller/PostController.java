package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.PostDTO;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.Visibility;
import com.example.PadelCaleruela.service.AuthService;
import com.example.PadelCaleruela.service.PostService;
import com.example.PadelCaleruela.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final UserService userService;

    private final AuthService authService;

    public PostController(PostService ps, UserService us,AuthService authService) {
        this.postService = ps;
        this.userService = us;
        this.authService=authService;
    }

    // ✅ Feed del usuario autenticado: posts de seguidos + propios + públicas de su ayuntamiento
    @GetMapping("/feed")
    public ResponseEntity<List<PostDTO>> getMyFeed() {
        User currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(postService.getFeed(currentUser));
    }

    // ⚠️ OPCIONAL: feed por userId solo para SuperAdmin (útil para panel de administración)
    @GetMapping("/feed/{userId}")
    public ResponseEntity<List<PostDTO>> getFeedForUser(@PathVariable Long userId) {
        User currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(postService.getFeedForUserId(userId, currentUser));
    }

    // 🔹 Feed público: solo posts PUBLIC del ayuntamiento del usuario autenticado
    @GetMapping("/feed/public")
    public ResponseEntity<List<PostDTO>> getPublicFeed() {
        User currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(postService.getPublicFeed(currentUser));
    }

    // 🔹 Posts del propio usuario autenticado
    @GetMapping("/me")
    public ResponseEntity<List<PostDTO>> getMyPosts() {
        User currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(
                postService.getPostsByUser(currentUser.getId(), currentUser)
        );
    }


    // 🔹 Posts de un usuario concreto (perfil público)
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDTO>> getPostsByUser(@PathVariable Long userId) {
        User currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(
                postService.getPostsByUser(userId, currentUser)
        );
    }

    @PatchMapping("/{postId}/visibility")
    public ResponseEntity<PostDTO> updateVisibility(
            @PathVariable Long postId,
            @RequestParam Visibility visibility) {
        User currentUser = authService.getCurrentUser();

        PostDTO updated = postService.updatePostVisibility(postId, visibility, currentUser);
        return ResponseEntity.ok(updated);
    }



    // 🔹 Obtener un post por id (respetando visibilidad)
    @GetMapping("/{id}")
    public ResponseEntity<?> getPostById(@PathVariable Long id) {
        User currentUser = authService.getCurrentUser();

        try {
            return ResponseEntity.ok(postService.getPostById(id, currentUser));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body("No tienes permiso para ver este post.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body("Post no encontrado.");
        }
    }


    // 🔹 Crear post
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createPost(
            @RequestPart("data") PostDTO postDTO,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        User currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(postService.createPost(postDTO, currentUser, image));
    }

    @PostMapping("/{postId}/seen")
    public ResponseEntity<Void> markAsSeen(@PathVariable Long postId) {
        User currentUser = authService.getCurrentUser();
        postService.markAsSeen(currentUser.getId(), postId);
        return ResponseEntity.ok().build();
    }


    // 🔹 Actualizar post
    @PutMapping("/{id}")
    public ResponseEntity<PostDTO> update(@PathVariable Long id, @RequestBody PostDTO postDTO) {
        User currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(postService.updatePost(id, postDTO, currentUser));
    }

    // 🔹 Eliminar post
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User currentUser = authService.getCurrentUser();
        postService.deletePost(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
