package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.RegisterDeviceTokenRequest;
import com.example.PadelCaleruela.model.DeviceToken;
import com.example.PadelCaleruela.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository repository;

    public DeviceTokenService(DeviceTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registerToken(RegisterDeviceTokenRequest req) {
        repository.findByToken(req.getToken())
                .ifPresent(repository::delete);

        DeviceToken deviceToken = new DeviceToken();
        deviceToken.setToken(req.token);
        deviceToken.setUserId(req.userId);
        deviceToken.setTenantId(req.tenantId);
        deviceToken.setPlatform(req.platform);
        deviceToken.setActive(true);
        deviceToken.setLastUsedAt(LocalDateTime.now());

        repository.save(deviceToken);
    }

    /** 🔹 Obtener todos los tokens activos de un usuario */
    public List<String> getTokensFromUser(Long userId) {
        return repository.findByUserIdAndActiveTrue(userId)
                .stream()
                .map(DeviceToken::getToken)
                .toList();
    }
}
