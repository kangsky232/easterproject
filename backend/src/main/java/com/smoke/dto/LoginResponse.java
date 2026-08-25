package com.smoke.dto;

public record LoginResponse(
        String token,
        String tokenType,
        CurrentUserResponse user) {

    @Override
    public String toString() {
        return "LoginResponse[token=[REDACTED], tokenType=" + tokenType + ", user=" + user + "]";
    }
}
