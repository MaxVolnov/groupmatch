package com.groupmatch.service;

import com.groupmatch.domain.PasswordResetToken;
import com.groupmatch.domain.User;
import com.groupmatch.exception.BadRequestException;
import com.groupmatch.repository.PasswordResetTokenRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.password-reset.token-ttl-hours:1}")
    private int tokenTtlHours;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isGuest()) return;

            tokenRepository.deleteAllByUserId(user.getId());

            PasswordResetToken prt = new PasswordResetToken();
            prt.setUser(user);
            prt.setToken(UUID.randomUUID());
            prt.setExpiresAt(Instant.now().plus(tokenTtlHours, ChronoUnit.HOURS));
            tokenRepository.save(prt);

            try {
                emailService.sendPasswordResetEmail(user.getEmail(), user.getDisplayName(), prt.getToken());
                log.info("Password reset email sent. userId={}", user.getId());
            } catch (Exception e) {
                log.warn("Password reset email not sent for userId={}. error={}", user.getId(), e.getMessage());
            }
        });
    }

    @Transactional
    public void resetPassword(UUID token, String newPassword) {
        PasswordResetToken prt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (prt.isUsed())    throw new BadRequestException("Token already used");
        if (prt.isExpired()) throw new BadRequestException("Token expired");

        prt.setUsedAt(Instant.now());
        tokenRepository.save(prt);

        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.invalidateAllForUser(user.getId());
        log.info("Password reset complete. userId={}", user.getId());
    }
}
