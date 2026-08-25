package com.smoke.security;

public record UserAccountPrincipal(
        String username,
        String role) {
}
