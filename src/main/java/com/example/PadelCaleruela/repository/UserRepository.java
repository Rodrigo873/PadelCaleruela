package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);


    boolean existsByEmail(String email);

    List<User> findByUsernameContainingIgnoreCase(String username);
    List<User> findByStatus(UserStatus status);
    @Query("SELECT u FROM User u WHERE u.username = :value OR u.email = :value")
    Optional<User> findByUsernameOrEmail(@Param("value") String value);

    List<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(String username, String fullName);

    Optional<User> findById(Long id);

}
