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

    @GetMapping("/feed/{userId}")
    public List<PostDTO> getFeed(@PathVariable Long userId) {
        return postService.getFeed(userId);
    }

    @GetMapping("/feed/public")
    public ResponseEntity<List<PostDTO>> getPublicFeed() {
        List<PostDTO> publicPosts = postService.getPublicFeed();
        return ResponseEntity.ok(publicPosts);
    }

    @PostMapping
    public PostDTO create(@RequestBody PostDTO postDTO) {
        return postService.createPost(postDTO);
    }
}
