package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Friendship;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Todos los amigos (aceptados o pendientes) que el usuario ha enviado
    @Query("SELECT f.friend.id FROM Friendship f WHERE f.user.id = :userId")
    List<Long> findFriendIdsByUserId(@Param("userId") Long userId);

    // Todos los amigos (aceptados o pendientes) que han enviado solicitud al usuario
    @Query("SELECT f.user.id FROM Friendship f WHERE f.friend.id = :userId")
    List<Long> findUserIdsByFriendId(@Param("userId") Long userId);

    // Solo los usuarios con amistad pendiente (enviada o recibida)
    @Query("SELECT CASE WHEN f.user.id = :userId THEN f.friend.id ELSE f.user.id END " +
            "FROM Friendship f WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = 'PENDING'")
    List<Long> findPendingFriendshipUserIds(@Param("userId") Long userId);


    Optional<Friendship> findByUserIdAndFriendId(Long userId, Long friendId);

    @Query("SELECT f.friend.id FROM Friendship f WHERE f.user.id = :userId AND f.status = 'ACCEPTED'")
    List<Long> findAcceptedFriendIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT f.user.id FROM Friendship f WHERE f.friend.id = :userId AND f.status = 'ACCEPTED'")
    List<Long> findAcceptedUserIdsByFriendId(@Param("userId") Long userId);

    /** 🔹 Todos los amigos de un grupo de usuarios */
    @Query("SELECT f.friend.id FROM Friendship f WHERE f.user.id IN :userIds AND f.status = 'ACCEPTED'")
    List<Long> findAcceptedFriendIdsByUserIds(@Param("userIds") List<Long> userIds);

    /** 🔹 Usuarios seguidos por mis amigos */
    @Query("SELECT DISTINCT f.friend.id FROM Friendship f WHERE f.user.id IN :userIds AND f.status = 'ACCEPTED'")
    List<Long> findFollowingOfFriends(@Param("userIds") List<Long> userIds);

    // 🔹 Usuarios que siguen al userId (quiénes lo tienen como "friend")
    @Query("SELECT f.user FROM Friendship f WHERE f.friend.id = :userId AND f.status = 'ACCEPTED'")
    List<User> findFollowersByUserId(Long userId);

    // 🔹 Usuarios a los que sigue el userId (quiénes son "friend" del user)
    @Query("SELECT f.friend FROM Friendship f WHERE f.user.id = :userId AND f.status = 'ACCEPTED'")
    List<User> findFollowingByUserId(Long userId);



}
