package com.groupmatch.service;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Role;
import com.groupmatch.domain.User;
import com.groupmatch.dto.auth.*;
import com.groupmatch.exception.EmailAlreadyExistsException;
import com.groupmatch.exception.ForbiddenException;
import com.groupmatch.exception.InvalidCredentialsException;
import com.groupmatch.repository.UserRepository;
import com.groupmatch.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    // ─── Signup ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse signup(SignupRequest request) {
        log.info("Signup attempt for email: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setTzId(request.tzid() != null ? request.tzid() : "Europe/Moscow");
        user.setPlan(Plan.FREE);
        user.setRole(Role.USER);
        user.setBlocked(false);

        user = userRepository.save(user);
        log.info("User created: userId={}", user.getId());

        return UserResponse.from(user);
    }

    // ─── Signin ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse signin(SigninRequest request) {
        log.info("Signin attempt for email: {}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.isBlocked()) {
            throw new InvalidCredentialsException("Account is blocked");
        }

        if (user.isBanned()) {
            throw new ForbiddenException("Account is banned");
        }

        log.info("User signed in: userId={}", user.getId());
        return issueTokenPair(user.getId(), user.getEmail(), user.getRole(), user.getPlan(), user.isGuest());
    }

    // ─── Guest signin ─────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse guestSignin(GuestRequest request) {
        String email = "guest-" + UUID.randomUUID() + "@guest.groupmatch.local";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDisplayName(request.displayName());
        user.setTzId("UTC");
        user.setPlan(Plan.FREE);
        user.setRole(Role.USER);
        user.setGuest(true);
        user.setBlocked(false);

        user = userRepository.save(user);
        log.info("Guest user created: userId={}", user.getId());

        return issueTokenPair(user.getId(), user.getEmail(), user.getRole(), user.getPlan(), user.isGuest());
    }

    // ─── Refresh ──────────────────────────────────────────────────────────────

    /**
     * Refresh token rotation:
     *   - Старый refresh token удаляется из Redis.
     *   - Выдаётся новая пара (access + refresh).
     *   - Если токен не найден → 401 (уже использован или истёк).
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest request) {
        UUID userId = refreshTokenService.validateAndRotate(request.refreshToken());

        if (userId == null) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (user.isBlocked()) {
            throw new InvalidCredentialsException("Account is blocked");
        }

        if (user.isBanned()) {
            throw new ForbiddenException("Account is banned");
        }

        log.info("Token refreshed for userId={}", userId);
        return issueTokenPair(user.getId(), user.getEmail(), user.getRole(), user.getPlan(), user.isGuest());
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    /**
     * Инвалидирует текущую сессию:
     *   - access token → Redis blacklist (TTL = оставшееся время жизни)
     *   - refresh token → удаляется из Redis
     */
    public void logout(String accessToken, String refreshToken) {
        long remainingTtl = jwtUtils.remainingTtlMillis(accessToken);
        refreshTokenService.blacklistAccessToken(accessToken, remainingTtl);

        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.invalidateRefresh(refreshToken);
        }

        UUID userId = jwtUtils.extractUserId(accessToken);
        log.info("User logged out: userId={}", userId);
    }

    // ─── Внутреннее ───────────────────────────────────────────────────────────

    private AuthResponse issueTokenPair(UUID userId, String email, Role role, Plan plan, boolean isGuest) {
        String accessToken  = jwtUtils.generateAccessToken(userId, email, role, plan, isGuest);
        String refreshToken = refreshTokenService.issue(userId);
        return new AuthResponse(accessToken, refreshToken, 900L);
    }
}
