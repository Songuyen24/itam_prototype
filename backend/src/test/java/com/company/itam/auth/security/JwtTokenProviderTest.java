package com.company.itam.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String TEST_SECRET =
            "this-is-a-very-long-test-secret-key-for-jwt-signing-must-be-32-bytes-or-more";
    private static final long TEST_TTL = 3_600_000L; // 1h

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(TEST_SECRET, TEST_TTL);
    }

    @Test
    void generateToken_andExtractClaims_success() {
        String token = provider.generateToken("admin@itam.example", "ADMIN");

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT phải có 3 phần header.payload.signature");
        assertEquals("admin@itam.example", provider.extractEmail(token));
        assertEquals("ADMIN", provider.extractRole(token));
        assertTrue(provider.validateToken(token));
    }

    @Test
    void validateToken_invalidSignature_returnsFalse() {
        String validToken = provider.generateToken("user01@itam.example", "USER");
        // Sửa phần signature để gây lỗi xác thực
        String tampered = validToken.substring(0, validToken.length() - 4) + "AAAA";
        assertFalse(provider.validateToken(tampered));
    }

    @Test
    void validateToken_garbageString_returnsFalse() {
        assertFalse(provider.validateToken("not-a-valid-jwt"));
        assertFalse(provider.validateToken(""));
    }

    @Test
    void validateToken_corruptedPayload_returnsFalse() {
        String validToken = provider.generateToken("test@example.com", "USER");
        // Đảo một ký tự trong phần payload
        char[] chars = validToken.toCharArray();
        chars[chars.length / 2] = chars[chars.length / 2] == 'a' ? 'b' : 'a';
        String tampered = new String(chars);
        assertFalse(provider.validateToken(tampered));
    }

    @Test
    void shortSecret_isPaddedToAtLeast32Bytes() throws Exception {
        JwtTokenProvider shortProvider = new JwtTokenProvider("short", TEST_TTL);
        String token = shortProvider.generateToken("user@example.com", "USER");
        assertTrue(shortProvider.validateToken(token));
    }
}
