package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);
    
    List<DeviceToken> findByUserIdAndActiveTrue(Long userId);
    void deleteByToken(String token);

    List<DeviceToken> findByUserId(Long userId);

}
