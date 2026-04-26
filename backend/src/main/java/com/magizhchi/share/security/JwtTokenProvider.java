package com.magizhchi.share.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey key;
    private final long      expirationMs;
    private final long      refreshExpiryMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.refresh-expiry-ms}") long refreshExpiryMs) {
        this.key            = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs   = expirationMs;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    public String generateAccessToken(Long userId) {
        return buildToken(userId, expirationMs, "access");
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(userId, refreshExpiryMs, "refresh");
    }

    private String buildToken(Long userId, long ttl, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl))
                .signWith(key)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(
                parseClaims(token).getSubject());
    }

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
