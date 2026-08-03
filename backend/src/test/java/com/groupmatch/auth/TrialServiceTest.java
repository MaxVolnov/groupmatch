package com.groupmatch.auth;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.User;
import com.groupmatch.service.TrialService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Правила выдачи псевдо-премиума. Юнит-тест, а не интеграционный: срок и флаг
 * приходят из конфига, и перебирать их через @TestPropertySource означало бы
 * поднимать по Spring-контексту на каждую комбинацию.
 */
public class TrialServiceTest {

    private static User newUser(boolean guest) {
        User u = new User();
        u.setPlan(Plan.FREE);
        u.setGuest(guest);
        return u;
    }

    @Test
    void grantsProForConfiguredNumberOfMonths() {
        User user = newUser(false);
        new TrialService(true, 3).grantTrial(user);

        assertThat(user.getPlan()).isEqualTo(Plan.PRO);
        assertThat(user.getTrialExpiresAt()).isNotNull();

        Instant expectedAbout = LocalDateTime.now(ZoneOffset.UTC).plusMonths(3).toInstant(ZoneOffset.UTC);
        assertThat(user.getTrialExpiresAt())
                .isCloseTo(expectedAbout, within(1, ChronoUnit.MINUTES));
    }

    /** Срок берётся из конфига — «три месяца» не зашиты в код. */
    @Test
    void durationComesFromConfiguration() {
        User user = newUser(false);
        new TrialService(true, 6).grantTrial(user);

        Instant expectedAbout = LocalDateTime.now(ZoneOffset.UTC).plusMonths(6).toInstant(ZoneOffset.UTC);
        assertThat(user.getTrialExpiresAt()).isCloseTo(expectedAbout, within(1, ChronoUnit.MINUTES));
    }

    /** Календарные месяцы, а не 30-дневные периоды. */
    @Test
    void usesCalendarMonths() {
        TrialService service = new TrialService(true, 1);
        Instant jan31 = Instant.parse("2026-01-31T12:00:00Z");
        assertThat(service.expiryFrom(jan31)).isEqualTo(Instant.parse("2026-02-28T12:00:00Z"));

        Instant nov15 = Instant.parse("2026-11-15T00:00:00Z");
        assertThat(new TrialService(true, 3).expiryFrom(nov15))
                .isEqualTo(Instant.parse("2027-02-15T00:00:00Z"));
    }

    @Test
    void doesNothingWhenDisabled() {
        User user = newUser(false);
        new TrialService(false, 3).grantTrial(user);

        assertThat(user.getPlan()).isEqualTo(Plan.FREE);
        assertThat(user.getTrialExpiresAt()).isNull();
    }

    /** Гость не может оплатить подписку — предлагать ему платный тариф нечего. */
    @Test
    void skipsGuestAccounts() {
        User guest = newUser(true);
        new TrialService(true, 3).grantTrial(guest);

        assertThat(guest.getPlan()).isEqualTo(Plan.FREE);
        assertThat(guest.getTrialExpiresAt()).isNull();
    }

    /** Повторный вызов не продлевает уже выданный триал. */
    @Test
    void doesNotRenewAnExistingTrial() {
        User user = newUser(false);
        TrialService service = new TrialService(true, 3);
        service.grantTrial(user);
        Instant first = user.getTrialExpiresAt();

        service.grantTrial(user);
        assertThat(user.getTrialExpiresAt()).isEqualTo(first);
    }

    /** Оплаченный PRO триалом не перетирается. */
    @Test
    void leavesPaidPlanAlone() {
        User user = newUser(false);
        user.setPlan(Plan.PRO);
        new TrialService(true, 3).grantTrial(user);

        assertThat(user.getTrialExpiresAt()).isNull();
    }

    @Test
    void rejectsNonPositiveDuration() {
        assertThatThrownBy(() -> new TrialService(true, 0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TrialService(true, -1))
                .isInstanceOf(IllegalStateException.class);
    }

    private static org.assertj.core.data.TemporalUnitOffset within(long amount, ChronoUnit unit) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(amount, unit);
    }
}
