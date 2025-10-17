package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Friendship;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship,Long> {


    // Lista todas las amistades aceptadas donde el usuario participa (como from o to)
    Optional<Friendship> findByUserAndFriend(User user, User friend);

    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.user.id = :userId OR f.friend.id = :userId)
          AND f.status = com.example.PadelCaleruela.model.FriendshipStatus.ACCEPTED
    """)
    List<Friendship> findAcceptedFriendshipsByUserId(Long userId);

    @Query("""
    SELECT f FROM Friendship f
    WHERE (f.user = :user AND f.friend = :friend)
       OR (f.user = :friend AND f.friend = :user)
""")
    Optional<Friendship> findExistingFriendship(User user, User friend);


}
