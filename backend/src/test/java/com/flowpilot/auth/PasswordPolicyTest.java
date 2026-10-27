package com.flowpilot.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.common.ApiException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    @Test
    void rejectsShortPasswords() {
        assertFalse(PasswordPolicy.violations("Ab1!", "a@b.com").isEmpty());
    }

    @Test
    void rejectsPasswordsWithTooFewCharacterClasses() {
        assertFalse(PasswordPolicy.violations("alllowercaseletters", "a@b.com").isEmpty());
    }

    @Test
    void rejectsPasswordContainingEmailName() {
        assertFalse(PasswordPolicy.violations("Alexander-Rocks-99!", "alexander@example.com").isEmpty());
    }

    @Test
    void acceptsStrongPassword() {
        assertTrue(PasswordPolicy.violations("Tr4vel-Light-Orbit!", "sam@example.com").isEmpty());
    }

    @Test
    void requireValidThrowsStructuredError() {
        ApiException ex = assertThrows(ApiException.class, () -> PasswordPolicy.requireValid("short", "a@b.com"));
        assertEquals("WEAK_PASSWORD", ex.code());
    }
}
