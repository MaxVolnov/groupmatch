package com.groupmatch.service;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Role;
import com.groupmatch.domain.User;
import com.groupmatch.dto.auth.*;
import com.groupmatch.exception.BadRequestException;
import com.groupmatch.exception.EmailAlreadyExistsException;
import com.groupmatch.exception.ForbiddenException;
import com.groupmatch.exception.InvalidCredentialsException;
import com.groupmatch.exception.UserNotFoundException;
import com.groupmatch.repository.UserRepository;
import com.groupmatch.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import com.groupmatch.util.TokenMasker;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final TrialService trialService;

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
        user.setLocale(request.locale() != null ? request.locale() : "ru");
        user.setPlan(Plan.FREE);
        user.setRole(Role.USER);
        user.setBlocked(false);
        trialService.grantTrial(user);

        user = userRepository.save(user);
        log.info("User created: userId={}, plan={}, trialUntil={}",
                user.getId(), user.getPlan(), user.getTrialExpiresAt());

        emailVerificationService.sendVerification(user);

        return UserResponse.from(user);
    }

    // ─── Signin ───────────────────────────────────────────────────────────────

    @Transactional
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

        touchActivity(user.getId());
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
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        log.debug("Token refresh requested. refreshToken={}", TokenMasker.mask(request.refreshToken()));
        UUID userId = refreshTokenService.validateAndRotate(request.refreshToken());

        if (userId == null) {
            log.warn("Refresh rejected: token not found or already used. token={}", TokenMasker.mask(request.refreshToken()));
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

        touchActivity(userId);
        log.info("Token refreshed. userId={}", userId);
        return issueTokenPair(user.getId(), user.getEmail(), user.getRole(), user.getPlan(), user.isGuest());
    }

    // ─── Guest upgrade ────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse upgradeGuest(UUID userId, UpgradeGuestRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (!user.isGuest()) {
            throw new BadRequestException("Account is already upgraded");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already in use");
        }

        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setGuest(false);
        user.setEmailVerified(false);
        // Апгрейд гостя — тот же момент «появился новый пользователь»,
        // иначе путь через гостевой вход молча лишал бы человека триала.
        trialService.grantTrial(user);
        userRepository.save(user);

        refreshTokenService.invalidateAllForUser(userId);
        AuthResponse newTokens = issueTokenPair(user.getId(), user.getEmail(), user.getRole(), user.getPlan(), false);

        emailVerificationService.sendVerification(user);

        log.info("Guest account upgraded. userId={}", userId);
        return newTokens;
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    /**
     * Инвалидирует текущую сессию:
     *   - access token → Redis blacklist (TTL = оставшееся время жизни)
     *   - refresh token → удаляется из Redis
     */
    public void logout(String accessToken, String refreshToken) {
        UUID userId = jwtUtils.extractUserId(accessToken);
        log.debug("Logout requested. userId={}", userId);

        long remainingTtl = jwtUtils.remainingTtlMillis(accessToken);
        refreshTokenService.blacklistAccessToken(accessToken, remainingTtl);

        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.invalidateRefresh(refreshToken);
        }

        log.info("User logged out: userId={}", userId);
    }

    // ─── Внутреннее ───────────────────────────────────────────────────────────

    /**
     * Отмечает активность пользователя. Вызывается на входе и на каждой ротации
     * refresh-токена (не чаще раза в 15 минут на сессию), поэтому дешёвая.
     * От этой отметки {@code GuestCleanupJob} считает срок жизни гостя.
     */
    private void touchActivity(UUID userId) {
        userRepository.touchLastActivity(userId, Instant.now());
    }

    private AuthResponse issueTokenPair(UUID userId, String email, Role role, Plan plan, boolean isGuest) {
        String accessToken  = jwtUtils.generateAccessToken(userId, email, role, plan, isGuest);
        String refreshToken = isGuest
                ? refreshTokenService.issueForGuest(userId)
                : refreshTokenService.issue(userId);
        return new AuthResponse(accessToken, refreshToken, 900L);
    }
}
