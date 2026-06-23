package com.groupmatch.config;

import com.groupmatch.domain.Role;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminPromotionRunner implements ApplicationRunner {

    private final UserRepository userRepository;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.info("ADMIN_EMAIL not set, skipping admin promotion");
            return;
        }
        userRepository.findByEmail(adminEmail).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                userRepository.save(user);
                log.info("User promoted to ADMIN. email={}", adminEmail);
            } else {
                log.info("User already ADMIN. email={}", adminEmail);
            }
        }, () -> log.warn("ADMIN_EMAIL set but user not found. email={}", adminEmail));
    }
}
