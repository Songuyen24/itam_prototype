package com.company.itam.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/** Signs and validates ITAM access tokens with the configured {@code jwt.secret}. */
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long accessTokenTtlMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret, @Value("${jwt.access-token-ttl}") long accessTokenTtlMillis) {
        // Accept both raw and Base64 secrets for existing deployments.
        byte[] keyBytes;
        if (secret.length() >= 64) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        } else {
            try {
                keyBytes = Decoders.BASE64.decode(secret);
            } catch (Exception ex) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        }
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtlMillis = accessTokenTtlMillis;
    }

    public String generateToken(String email, String role) {
        return generateToken(email, role, 0);
    }

    public String generateToken(String email, String role, long accessVersion) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiration = new Date(now + accessTokenTtlMillis);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim(ROLE_CLAIM, role)
                .claim("accessVersion", accessVersion)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Retains the old method name for callers; the subject is now a stable principal. */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public long extractAccessVersion(String token) {
        Number version = extractClaims(token).get("accessVersion", Number.class);
        return version == null ? 0 : version.longValue();
    }

    public String extractRole(String token) {
        Object role = extractClaims(token).get(ROLE_CLAIM);
        return role == null ? null : role.toString();
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
