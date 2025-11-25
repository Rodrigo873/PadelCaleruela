package com.example.PadelCaleruela.repository;


import com.example.PadelCaleruela.model.Post;
import com.example.PadelCaleruela.model.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 🔹 Posts públicos
    List<Post> findByVisibility(Visibility visibility);

    // 🔹 Posts de los amigos (usuarios con amistad aceptada)
    @Query("""
        SELECT p FROM Post p
        WHERE p.user.id IN (
            SELECT CASE
                WHEN f.user.id = :userId THEN f.friend.id
                ELSE f.user.id
            END
            FROM Friendship f
            WHERE (f.user.id = :userId OR f.friend.id = :userId)
            AND f.status = com.example.PadelCaleruela.model.FriendshipStatus.ACCEPTED
        )
        OR p.user.id = :userId
        ORDER BY p.createdAt DESC
    """)
    List<Post> findFeedForUser(@Param("userId") Long userId);

    // Usado en feed: ya tendrás una query que mezcla amigos + propios + etc.

    List<Post> findByUserId(Long userId);


}
