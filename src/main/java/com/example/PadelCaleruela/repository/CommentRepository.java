package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    List<Comment> findTop3ByPostIdOrderByCreatedAtDesc(Long postId);

}
