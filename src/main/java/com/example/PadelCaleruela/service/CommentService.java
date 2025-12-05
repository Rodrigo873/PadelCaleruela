package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.CommentResponse;
import com.example.PadelCaleruela.dto.CreateCommentRequest;
import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Comment;
import com.example.PadelCaleruela.model.Post;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.BlockRepository;
import com.example.PadelCaleruela.repository.CommentRepository;
import com.example.PadelCaleruela.repository.PostRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
public class CommentService {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    UserService userService;

    @Autowired
    BlockRepository blockRepository;
    @Autowired
    AuthService authService;

    public CommentResponse createComment(Long postId, CreateCommentRequest request) {

        User viewer = authService.getCurrentUser();               // 🔥 usuario logueado
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        validateCanViewPost(viewer, post);

        // prevenir suplantación
        if (!viewer.getId().equals(request.getUserId())) {
            throw new RuntimeException("Intento de suplantación detectado.");
        }

        Comment c = new Comment();
        c.setPost(post);
        c.setUser(viewer);
        c.setText(request.getText());

        Comment saved = commentRepository.save(c);
        return toDto(saved);
    }


    public List<CommentResponse> getComments(Long postId) {

        User viewer = authService.getCurrentUser();     // 🔥
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        validateCanViewPost(viewer, post);

        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(this::toDto)
                .toList();
    }



    public List<CommentResponse> getLastThreeComments(Long postId) {

        User viewer = authService.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        validateCanViewPost(viewer, post);

        return commentRepository.findTop3ByPostIdOrderByCreatedAtDesc(postId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public void deleteComment(Long commentId) {

        User requester = authService.getCurrentUser();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        User commentOwner = comment.getUser();
        User postOwner = comment.getPost().getUser();

        boolean isCommentOwner = requester.getId().equals(commentOwner.getId());
        boolean isPostOwner = requester.getId().equals(postOwner.getId());
        boolean isSuper = authService.isSuperAdmin();

        if (!isCommentOwner && !isPostOwner && !isSuper) {
            throw new RuntimeException("No tienes permiso para eliminar este comentario.");
        }

        commentRepository.delete(comment);
    }


    private void validateCanViewPost(User viewer, Post post) {

        if (viewer == null || post == null) {
            throw new RuntimeException("No autorizado.");
        }

        User owner = post.getUser();

        // ⭐ SUPERADMIN ve todo
        if (authService.isSuperAdmin()) return;

        // ⭐ Deben pertenecer al mismo ayuntamiento
        if (viewer.getAyuntamiento() == null ||
                owner.getAyuntamiento() == null ||
                !viewer.getAyuntamiento().getId().equals(owner.getAyuntamiento().getId())) {
            throw new RuntimeException("No puedes ver publicaciones de otros ayuntamientos.");
        }

        // ⭐ Bloqueos personales
        if (blockRepository.existsByBlockedByUserAndBlockedUser(owner, viewer)) {
            throw new RuntimeException("El creador te ha bloqueado.");
        }

        if (blockRepository.existsByBlockedByUserAndBlockedUser(viewer, owner)) {
            throw new RuntimeException("Has bloqueado al creador.");
        }

        // ⭐ Bloqueos por ayuntamiento
        if (blockRepository.existsByBlockedByAyuntamientoAndBlockedUser(owner.getAyuntamiento(), viewer)) {
            throw new RuntimeException("Tu ayuntamiento no permite ver este contenido.");
        }

        // ⭐ Visibilidad
        switch (post.getVisibility()) {

            case PRIVATE:
                if (!viewer.getId().equals(owner.getId())) {
                    throw new RuntimeException("Esta publicación es privada.");
                }
                break;

            case FRIENDS:
                boolean follows = userService.isFollowing(viewer.getId(), owner.getId());
                if (!follows) {
                    throw new RuntimeException("Solo seguidores pueden ver esta publicación.");
                }
                break;

            case PUBLIC:
                break;
        }
    }


    private CommentResponse toDto(Comment c) {
        CommentResponse dto = new CommentResponse();
        dto.setId(c.getId());
        dto.setText(c.getText());
        dto.setCreatedAt(c.getCreatedAt());

        dto.setUserId(c.getUser().getId());
        dto.setUsername(c.getUser().getUsername());
        dto.setProfileImageUrl(c.getUser().getProfileImageUrl());

        return dto;
    }



}
