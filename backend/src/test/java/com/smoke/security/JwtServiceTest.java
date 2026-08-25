package com.smoke.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "a-development-secret-that-is-longer-than-thirty-two-bytes",
            60_000L);

    @Test
    void passwordHashChangeInvalidatesExistingToken() {
        String token = jwtService.createToken(
                new UserAccountPrincipal("admin", "SYSTEM_ADMIN"),
                "$2a$10$old-password-hash");

        var claims = jwtService.parse(token);

        assertTrue(jwtService.credentialMatches(claims, "$2a$10$old-password-hash"));
        assertFalse(jwtService.credentialMatches(claims, "$2a$10$new-password-hash"));
    }
}
