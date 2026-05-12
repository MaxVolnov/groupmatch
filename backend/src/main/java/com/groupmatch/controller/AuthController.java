package com.groupmatch.controller;

import com.groupmatch.dto.auth.*;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /api/v1/auth/signup — регистрация (публичный). */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    /** POST /api/v1/auth/signin — вход; возвращает access + refresh token (публичный). */
    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@Valid @RequestBody SigninRequest request) {
        return ResponseEntity.ok(authService.signin(request));
    }

    /**
     * POST /api/v1/auth/refresh — обмен refresh token на новую пару токенов (публичный).
     * Реализует rotation: старый refresh token аннулируется.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /**
     * POST /api/v1/auth/logout — выход (требует авторизации).
     * Access token → Redis blacklist.
     * Refresh token → удаляется из Redis (передаётся в теле, опционально).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) RefreshRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String accessToken  = authHeader.substring(7); // strip "Bearer "
        String refreshToken = body != null ? body.refreshToken() : null;
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }
}
