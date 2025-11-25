package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.UserSeenPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSeenPostRepository extends JpaRepository<UserSeenPost, Long> {
    boolean existsByUserIdAndPostId(Long userId, Long postId);
}
