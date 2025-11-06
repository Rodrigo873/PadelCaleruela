package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.PasswordResetToken;
import com.example.PadelCaleruela.model.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    @Modifying
    @Transactional
    void deleteAllByUser(User user);
}
