package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Like;
import com.example.PadelCaleruela.model.Post;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserAndPost(User user, Post post);

    void deleteByUserAndPost(User user, Post post);

    long countByPost(Post post);

    List<Like> findByPost(Post post);
}
