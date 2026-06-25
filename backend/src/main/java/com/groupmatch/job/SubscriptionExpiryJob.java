package com.groupmatch.job;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Subscription;
import com.groupmatch.domain.SubscriptionStatus;
import com.groupmatch.repository.SubscriptionRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryJob {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void expireSubscriptions() {
        List<Subscription> expired = subscriptionRepository
                .findByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, Instant.now());
        if (expired.isEmpty()) return;

        log.info("Expiring {} subscriptions", expired.size());
        for (Subscription sub : expired) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);

            boolean hasOtherActive = subscriptionRepository.existsByUserIdAndStatusAndIdNot(
                    sub.getUser().getId(), SubscriptionStatus.ACTIVE, sub.getId());
            if (!hasOtherActive) {
                sub.getUser().setPlan(Plan.FREE);
                userRepository.save(sub.getUser());
                log.info("User plan downgraded to FREE. userId={}", sub.getUser().getId());
            }
        }
    }
}
