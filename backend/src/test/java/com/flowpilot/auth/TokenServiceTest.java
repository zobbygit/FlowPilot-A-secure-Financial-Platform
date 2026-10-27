package com.flowpilot.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.security.TokenService;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenServiceTest {
    private final TokenService service = new TokenService();

    @Test
    void issuedTokensAreUniqueAndNotStoredInTheClear() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            TokenService.IssuedToken t = service.issue();
            assertTrue(seen.add(t.raw()), "raw tokens must not repeat");
            assertNotEquals(t.raw(), t.hash());
            assertEquals(64, t.hash().length());            // SHA-256 hex
            assertTrue(t.raw().length() >= 43);             // 256 bits, base64url
        }
    }

    @Test
    void hashIsDeterministic() {
        assertEquals(service.hash("abc"), service.hash("abc"));
        assertNotEquals(service.hash("abc"), service.hash("abd"));
    }
}
