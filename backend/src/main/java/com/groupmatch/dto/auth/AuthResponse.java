package com.groupmatch.dto.auth;

public record AuthResponse(
        String accessToken,
        Long expiresIn,
        String tokenType
) {
    public AuthResponse(String accessToken, Long expiresIn) {
        this(accessToken, expiresIn, "Bearer");
    }
}