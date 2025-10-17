package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.PostDTO;
import com.example.PadelCaleruela.model.Post;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.Visibility;
import com.example.PadelCaleruela.repository.PostRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostService(PostRepository repo,UserRepository userRepository) {
        this.postRepository = repo;
        this.userRepository=userRepository;
    }

    // 🔹 Feed personalizado: posts de amigos + propios
    public List<PostDTO> getFeed(Long userId) {
        List<Post> posts = postRepository.findFeedForUser(userId);
        return posts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 🔹 Feed público: visible para cualquiera
    public List<PostDTO> getPublicFeed() {
        List<Post> posts = postRepository.findByVisibility(Visibility.PUBLIC);
        return posts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 🔹 Crear post
    public PostDTO createPost(PostDTO postDTO) {
        User user = userRepository.findById(postDTO.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        Post post = new Post();
        post.setUser(user);
        post.setMessage(postDTO.getMessage());
        post.setMatchResult(postDTO.getMatchResult());
        post.setVisibility(postDTO.getVisibility() != null ? postDTO.getVisibility() : Visibility.PUBLIC);
        post.setCreatedAt(LocalDateTime.now());

        Post saved = postRepository.save(post);
        return convertToDTO(saved);
    }

    // 🔹 Conversión entidad → DTO
    private PostDTO convertToDTO(Post post) {
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        dto.setMessage(post.getMessage());
        dto.setMatchResult(post.getMatchResult());
        dto.setVisibility(post.getVisibility());
        dto.setUserId(post.getUser().getId());
        dto.setCreatedAt(post.getCreatedAt());
        return dto;
    }

}
