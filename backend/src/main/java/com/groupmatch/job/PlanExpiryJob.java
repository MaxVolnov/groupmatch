package com.groupmatch.job;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.SubscriptionStatus;
import com.groupmatch.domain.User;
import com.groupmatch.repository.SubscriptionRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Закрывает псевдо-премиум, выданный при регистрации ({@code TrialService}).
 *
 * Отдельно от {@link SubscriptionExpiryJob}: тот работает по таблице
 * {@code subscription} (оплаченные подписки), здесь же оплаты не было вовсе —
 * есть только срок в {@code app_user.trial_expires_at}.
 *
 * Триал гасится всегда, а вот план откатывается на FREE только если за это
 * время не появилась активная подписка: иначе джоб отбирал бы PRO у человека,
 * который заплатил ещё во время триала.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanExpiryJob {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * На полчаса позже SubscriptionExpiryJob: платные подписки закрываются
     * первыми. Расписание в конфиге — чтобы на стенде можно было ускорить
     * прогон, не пересобирая приложение.
     */
    @Scheduled(cron = "${app.trial.expiry-cron:0 30 2 * * *}")
    @Transactional
    public void expireTrials() {
        List<User> expired = userRepository.findByTrialExpiresAtBefore(Instant.now());
        if (expired.isEmpty()) {
            log.debug("Trial expiry: nothing to do");
            return;
        }

        int downgraded = 0;
        for (User user : expired) {
            // Триал отработан в любом случае — иначе джоб цеплял бы этот аккаунт
            // каждую ночь до скончания века.
            user.setTrialExpiresAt(null);

            boolean hasPaidSubscription = subscriptionRepository
                    .existsByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);
            if (hasPaidSubscription) {
                log.info("Trial ended, paid subscription active — plan kept. userId={}", user.getId());
            } else if (user.getPlan() != Plan.FREE) {
                user.setPlan(Plan.FREE);
                downgraded++;
                log.info("Trial ended, plan downgraded to FREE. userId={}", user.getId());
            }
            userRepository.save(user);
        }

        log.info("Trial expiry: processed {} accounts, downgraded {}", expired.size(), downgraded);
    }
}
