package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.CustomUserDetails;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    // 🔐 Clave REAL de 256 bits en Base64 (segura y válida)
    // Usa:  Base64.getEncoder().encodeToString(SecureRandom.generateSeed(32))
    private static final String SECRET_KEY =
            "pX8nGqv9wU2bR5kEj6VCnM8cz4Q9sGj7qYdLcTsDkPwHfRnUpZx1Lq3BmN0sY2aP";

    // ⏳ Duración recomendada (7 días)
    private static final long EXPIRATION_MS = 1000L * 60 * 60 * 24 * 7;

    // ============================================================
    // EXTRACTORES
    // ============================================================

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Long extractAyuntamientoId(String token) {
        return extractClaim(token, claims -> claims.get("ayuntamientoId", Long.class));
    }

    // ============================================================
    // GENERAR TOKEN
    // ============================================================

    public String generateToken(UserDetails userDetails) {
        CustomUserDetails custom = (CustomUserDetails) userDetails;

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", custom.getUser().getId());
        claims.put("role", custom.getUser().getRole().name());
        claims.put("ayuntamientoId",
                custom.getUser().getAyuntamiento() != null
                        ? custom.getUser().getAyuntamiento().getId()
                        : null
        );

        return createToken(claims, custom.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + EXPIRATION_MS))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ============================================================
    // VALIDACIÓN
    // ============================================================

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false; // token manipulado, expirado, inválido, etc.
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    // ============================================================
    // CLAIMS INTERNOS
    // ============================================================

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
