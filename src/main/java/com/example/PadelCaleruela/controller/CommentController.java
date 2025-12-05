package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.CommentResponse;
import com.example.PadelCaleruela.dto.CreateCommentRequest;
import com.example.PadelCaleruela.model.Comment;
import com.example.PadelCaleruela.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @PostMapping("/{postId}")
    public CommentResponse create(
            @PathVariable Long postId,
            @RequestBody CreateCommentRequest request
    ) {
        return commentService.createComment(postId, request);
    }

    @GetMapping("/{postId}")
    public List<CommentResponse> list(@PathVariable Long postId) {
        return commentService.getComments(postId);
    }

    @GetMapping("/{postId}/last3")
    public List<CommentResponse> getLastTwo(@PathVariable Long postId) {
        return commentService.getLastThreeComments(postId);
    }

    @DeleteMapping("/{commentId}")
    public void deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
    }

}
