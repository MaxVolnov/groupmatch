package com.groupmatch.service;

import com.groupmatch.domain.EmailVerificationToken;
import com.groupmatch.domain.User;
import com.groupmatch.exception.BadRequestException;
import com.groupmatch.repository.EmailVerificationTokenRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.email-verification.token-ttl-hours:24}")
    private int tokenTtlHours;

    @Transactional
    public void sendVerification(User user) {
        if (user.isGuest()) return;
        if (user.isEmailVerified()) return;

        tokenRepository.deleteAllByUserId(user.getId());

        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(UUID.randomUUID());
        evt.setExpiresAt(Instant.now().plus(tokenTtlHours, ChronoUnit.HOURS));
        tokenRepository.save(evt);

        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getDisplayName(), evt.getToken());
            log.info("Verification email sent. userId={}", user.getId());
        } catch (Exception e) {
            log.warn("Verification email not sent for userId={}. Token saved, can resend. error={}", user.getId(), e.getMessage());
        }
    }

    @Transactional
    public void verifyToken(UUID token) {
        EmailVerificationToken evt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification token"));

        if (evt.isUsed())    throw new BadRequestException("Token already used");
        if (evt.isExpired()) throw new BadRequestException("Token expired");

        evt.setUsedAt(Instant.now());
        tokenRepository.save(evt);

        User user = evt.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("Email verified. userId={}", user.getId());
    }
}
