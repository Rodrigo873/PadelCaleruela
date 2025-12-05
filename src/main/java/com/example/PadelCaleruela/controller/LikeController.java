package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/{postId}")
    public ResponseEntity<?> like(@PathVariable Long postId) {
        likeService.likePost(postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> unlike(@PathVariable Long postId) {
        likeService.unlikePost(postId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{postId}")
    public ResponseEntity<Long> getLikes(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.getLikes(postId));
    }
}
