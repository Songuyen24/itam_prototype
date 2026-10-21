package com.company.itam.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Sinh và xác thực JWT access token dùng cho prototype ITAM.
 *
 * <p>Token payload bao gồm:
 * <ul>
 *     <li>{@code sub} = định danh ổn định user:&lt;id&gt;</li>
 *     <li>claim {@code role} = mã role (ADMIN / IT_STAFF / PUR_STAFF / USER)</li>
 *     <li>{@code jti} = mã định danh duy nhất của token</li>
 * </ul>
 *
 * <p>Thuật toán ký: HMAC-SHA256 với khóa bí mật cấu hình trong {@code jwt.secret}.
 */
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long accessTokenTtlMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-ttl}") long accessTokenTtlMillis
    ) {
        // Chấp nhận cả Base64 và chuỗi thô. Nếu dài >= 64 bytes thì dùng trực tiếp,
        // ngược lại fallback Base64 decode.
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

    /**
     * Sinh JWT token với định danh ổn định và role tương ứng.
     *
     * @param email định danh principal (user:&lt;id&gt;) làm JWT subject
     * @param role  mã role (làm claim phụ)
     * @return chuỗi JWT đã ký
     */
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

    /**
     * Trích principal từ JWT token (tên hàm giữ để tương thích caller).
     *
     * @param token JWT đã ký
     * @return principal được lưu trong subject
     */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public long extractAccessVersion(String token) {
        Number version = extractClaims(token).get("accessVersion", Number.class);
        return version == null ? 0 : version.longValue();
    }

    /**
     * Trích mã role từ JWT token.
     *
     * @param token JWT đã ký
     * @return mã role
     */
    public String extractRole(String token) {
        Object role = extractClaims(token).get(ROLE_CLAIM);
        return role == null ? null : role.toString();
    }

    /**
     * Xác thực chữ ký và thời hạn của JWT token.
     *
     * @param token JWT đã ký
     * @return true nếu hợp lệ và chưa hết hạn
     */
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
