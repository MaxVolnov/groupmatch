package com.groupmatch.service;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.groupmatch.util.PlanPeriod;

import java.time.Instant;

/**
 * Псевдо-премиум для первых пользователей: при регистрации аккаунт получает
 * {@link Plan#PRO} на ограниченный срок. По истечении {@code PlanExpiryJob}
 * возвращает аккаунт на FREE, если к тому моменту не куплена подписка.
 *
 * Срок и сам факт выдачи — в конфиге ({@code app.trial.*}), чтобы «три месяца»
 * можно было поменять на шесть переменной окружения, без релиза.
 *
 * Гостям триал не выдаётся: у гостевого аккаунта нет email, оплатить подписку
 * он не может, и предлагать ему платный тариф бессмысленно.
 */
@Service
@Slf4j
public class TrialService {

    private final boolean trialEnabled;
    private final int trialDurationMonths;

    public TrialService(
            @Value("${app.trial.enabled:true}") boolean trialEnabled,
            @Value("${app.trial.duration-months:3}") int trialDurationMonths) {
        this.trialEnabled = trialEnabled;
        this.trialDurationMonths = trialDurationMonths;
        if (trialDurationMonths <= 0) {
            throw new IllegalStateException("app.trial.duration-months must be positive, got " + trialDurationMonths);
        }
        log.info("Trial premium: enabled={}, durationMonths={}", trialEnabled, trialDurationMonths);
    }

    public int durationMonths() {
        return trialDurationMonths;
    }

    /**
     * Выдаёт триальный PRO. Вызывается на регистрации и на апгрейде гостя до
     * полноценного аккаунта — это тот же момент «пришёл новый пользователь»,
     * и без этого путь через гостя молча лишал бы человека триала.
     *
     * Сущность только меняется в памяти; сохранение — на вызывающей стороне.
     */
    public void grantTrial(User user) {
        if (!trialEnabled) return;
        if (user.isGuest()) return;
        if (user.getTrialExpiresAt() != null) return; // триал уже выдавался
        if (user.getPlan() != Plan.FREE) return;      // уже платный — не трогаем

        user.setPlan(Plan.PRO);
        user.setTrialExpiresAt(expiryFrom(Instant.now()));
        log.info("Trial premium granted. userId={}, until={}", user.getId(), user.getTrialExpiresAt());
    }

    /**
     * Календарные месяцы, а не 30-дневные периоды: «три месяца» в баннере
     * должно означать то же, что и в календаре пользователя. Само вычисление —
     * в {@link PlanPeriod}: то же определение месяца использует оплаченная
     * подписка.
     */
    public Instant expiryFrom(Instant from) {
        return PlanPeriod.advance(from, trialDurationMonths);
    }
}
