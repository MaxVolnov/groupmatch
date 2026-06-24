package com.groupmatch.controller;

import com.groupmatch.domain.User;
import com.groupmatch.dto.auth.*;
import com.groupmatch.exception.UserNotFoundException;
import com.groupmatch.repository.UserRepository;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.AuthService;
import com.groupmatch.service.EmailVerificationService;
import com.groupmatch.service.PasswordResetService;
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
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;

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

    /** POST /api/v1/auth/guest — мгновенный гостевой аккаунт без email/пароля (публичный). */
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse guestSignin(@Valid @RequestBody GuestRequest request) {
        return authService.guestSignin(request);
    }

    /**
     * POST /api/v1/auth/refresh — обмен refresh token на новую пару токенов (публичный).
     * Реализует rotation: старый refresh token аннулируется.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyToken(request.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(UserNotFoundException::new);
        emailVerificationService.sendVerification(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upgrade-guest")
    public ResponseEntity<AuthResponse> upgradeGuest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpgradeGuestRequest request) {
        return ResponseEntity.ok(authService.upgradeGuest(principal.getId(), request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/auth/logout — выход (требует авторизации).
     * Access token → Redis blacklist (TTL = оставшееся время жизни).
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
