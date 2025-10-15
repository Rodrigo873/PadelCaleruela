package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.PostDTO;
import com.example.PadelCaleruela.model.Post;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.Visibility;
import com.example.PadelCaleruela.repository.PostRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;

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

    public List<PostDTO> getFeed(Long userId) {
        List<Post> posts = postRepository.findFeedForUser(userId);
        return posts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public PostDTO createPost(PostDTO postDTO) {
        Optional<User> user = userRepository.findById(postDTO.getUserId());
        if (user.isEmpty()){
            throw new RuntimeException("El usuario que ha creado el post no existe");
        }
        Post post = new Post();
        post.setUser(user.get());
        post.setMessage(postDTO.getMessage());
        post.setMatchResult(postDTO.getMatchResult());
        post.setVisibility(postDTO.getVisibility());
        post.setCreatedAt(postDTO.getCreatedAt());
        Post post1=postRepository.save(post);
        return convertToDTO(post1);
    }


    public List<PostDTO> getPublicFeed() {
        List<Post> posts = postRepository.findByVisibility(Visibility.PUBLIC);
        return posts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

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
