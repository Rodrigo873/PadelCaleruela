package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.dto.PostDTO;
import com.example.PadelCaleruela.model.Post;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.service.PostService;
import com.example.PadelCaleruela.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = "http://localhost:4200")
public class PostController {

    private final PostService postService;
    private final UserService userService;

    public PostController(PostService ps, UserService us) {
        this.postService = ps;
        this.userService = us;
    }

    // 🔹 Feed de amigos
    @GetMapping("/feed/{userId}")
    public ResponseEntity<List<PostDTO>> getFeed(@PathVariable Long userId) {
        return ResponseEntity.ok(postService.getFeed(userId));
    }

    // 🔹 Feed público
    @GetMapping("/feed/public")
    public ResponseEntity<List<PostDTO>> getPublicFeed() {
        return ResponseEntity.ok(postService.getPublicFeed());
    }

    // 🔹 Crear post
    @PostMapping
    public ResponseEntity<PostDTO> create(@RequestBody PostDTO postDTO) {
        return ResponseEntity.ok(postService.createPost(postDTO));
    }
}
