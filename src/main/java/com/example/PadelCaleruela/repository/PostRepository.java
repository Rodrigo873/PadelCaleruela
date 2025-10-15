package com.example.PadelCaleruela.repository;


import com.example.PadelCaleruela.model.Post;
import com.example.PadelCaleruela.model.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
    SELECT p FROM Post p
    WHERE p.visibility = com.example.PadelCaleruela.model.Visibility.PUBLIC
       OR p.user.id IN (
           SELECT f.friend.id FROM Friendship f WHERE f.user.id = :userId
       )
    ORDER BY p.createdAt DESC
""")
    List<Post> findFeedForUser(Long userId);

    List<Post> findByVisibility(Visibility visibility);


}
