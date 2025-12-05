package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.WelockAuthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Service
public class WelockAuthService {

    @Value("${welock.base-url}")
    private String baseUrl;

    @Value("${welock.app-id}")
    private String appId;

    @Value("${welock.secret}")
    private String secret;

    private String accessToken;
    private String refreshToken;
    private Instant expiresAt;

    private final WebClient client;

    public WelockAuthService(WebClient.Builder builder) {
        this.client = builder.build();
    }

    // ======================================================
    // 🔑 Obtener Token válido
    // ======================================================
    public synchronized String getToken() {
        if (accessToken != null && expiresAt != null && Instant.now().isBefore(expiresAt)) {
            return accessToken;
        }

        if (refreshToken != null) {
            return refreshToken();
        }

        return requestNewToken();
    }

    // ======================================================
    // 🆕 Obtener un token nuevo
    // ======================================================
    private String requestNewToken() {
        Map<String, String> body = Map.of(
                "appID", appId,
                "secret", secret
        );

        String url = baseUrl + "/API/Auth/Token";

        WelockAuthResponse resp = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WelockAuthResponse.class)
                .block();

        if (resp == null || resp.getCode() != 0) {
            throw new RuntimeException("Error autenticando con Welock: " + resp);
        }

        accessToken = resp.getData().getAccessToken();
        refreshToken = resp.getData().getRefreshToken();
        expiresAt = Instant.now().plusSeconds(resp.getData().getExpiresIn());

        return accessToken;
    }

    // ======================================================
    // 🔁 Refrescar token
    // ======================================================
    private String refreshToken() {

        Map<String, String> body = Map.of(
                "appID", appId,
                "refreshToken", refreshToken
        );

        String url = baseUrl + "/API/Auth/RefreshToken";

        WelockAuthResponse resp = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WelockAuthResponse.class)
                .block();

        if (resp == null || resp.getCode() != 0) {
            return requestNewToken();
        }

        accessToken = resp.getData().getAccessToken();
        refreshToken = resp.getData().getRefreshToken();
        expiresAt = Instant.now().plusSeconds(resp.getData().getExpiresIn());

        return accessToken;
    }

    // ======================================================
    // 🔍 GETTERS que faltaban
    // ======================================================

    public String getAccessTokenValue() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
