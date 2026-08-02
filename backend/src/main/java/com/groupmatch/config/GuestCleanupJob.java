package com.groupmatch.config;

import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuestCleanupJob {

    private final UserRepository userRepository;

    /**
     * Срок неактивности, после которого гостевой аккаунт удаляется.
     * Совпадает с TTL гостевого refresh-токена (90 дней): пока живёт сессия,
     * живут и данные.
     */
    @Value("${app.guest.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 3 * * *") // every day at 03:00 UTC
    @Transactional
    public void cleanupExpiredGuestAccounts() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = userRepository.deleteGuestAccountsInactiveSince(cutoff);
        if (deleted > 0) {
            log.info("Guest cleanup: deleted {} guest accounts inactive for more than {} days",
                    deleted, retentionDays);
        } else {
            log.debug("Guest cleanup: no inactive guest accounts found");
        }
    }
}
