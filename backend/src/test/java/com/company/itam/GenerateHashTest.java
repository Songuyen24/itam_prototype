package com.company.itam;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class GenerateHashTest {
    @Test
    void verifySeedHash() {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(12);
        String hash = "$2a$12$ap67CmIDlhWC9EQKLs1X4uUi7SRhvFd6C42Umidu2dgtkwv6IPVLO";
        boolean ok = enc.matches("Password@123", hash);
        System.out.println("MATCH=" + ok);
        System.out.println("NEW_HASH=" + enc.encode("Password@123"));
        if (!ok) {
            throw new AssertionError("Hash in V8 does NOT match 'Password@123'");
        }
    }
}
