package com.groupmatch.dto.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long expiresIn,      // секунды до истечения access token
        String tokenType
) {
    /** Конструктор для signin/refresh: обе пары токенов. */
    public AuthResponse(String accessToken, String refreshToken, Long expiresIn) {
        this(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
