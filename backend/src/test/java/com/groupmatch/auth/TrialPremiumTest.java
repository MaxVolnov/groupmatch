package com.groupmatch.auth;

import com.groupmatch.BaseIntegrationTest;
import com.groupmatch.job.PlanExpiryJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Псевдо-премиум сквозь весь путь: регистрация выдаёт PRO с датой окончания,
 * {@link PlanExpiryJob} по истечении возвращает на FREE, а купленную подписку
 * при этом не трогает.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TrialPremiumTest extends BaseIntegrationTest {

    private static final String EMAIL = "trial-user@groupmatch-test.io";
    private static final String UPGRADED_EMAIL = "trial-upgraded@groupmatch-test.io";
    private static final String PASSWORD = "TrialUser1!";

    @Autowired
    PlanExpiryJob planExpiryJob;

    private String accessToken;

    @BeforeAll
    void setUp() {
        cleanupUser(EMAIL);
        cleanupUser(UPGRADED_EMAIL);
    }

    @AfterAll
    void tearDown() {
        cleanupUser(EMAIL);
        cleanupUser(UPGRADED_EMAIL);
    }

    // ── Выдача ───────────────────────────────────────────────────────────────

    @Test @Order(1)
    void signupGrantsProWithTrialExpiry() {
        var resp = rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Trial User"), jsonHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("plan")).isEqualTo("PRO");
        assertThat(resp.getBody().get("trialExpiresAt")).isNotNull();

        assertThat(planInDb(EMAIL)).isEqualTo("PRO");
        // Дефолт — 3 календарных месяца.
        Instant expected = LocalDateTime.now(ZoneOffset.UTC).plusMonths(3).toInstant(ZoneOffset.UTC);
        assertThat(trialInDb(EMAIL)).isCloseTo(expected, within(5, ChronoUnit.MINUTES));
    }

    /** План едет в JWT — токен свежего пользователя должен быть уже PRO. */
    @Test @Order(2)
    void issuedTokenCarriesProPlan() {
        accessToken = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD), jsonHeaders()),
                        Map.class)
                .getBody().get("accessToken").toString();

        var me = rest.exchange(url("/api/v1/me"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), Map.class);
        assertThat(me.getBody().get("plan")).isEqualTo("PRO");
        assertThat(me.getBody().get("trialExpiresAt")).isNotNull();

        var plan = rest.exchange(url("/api/v1/me/plan"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), Map.class);
        assertThat(plan.getBody().get("plan")).isEqualTo("PRO");
        assertThat(plan.getBody().get("groupLimit")).isEqualTo(-1); // безлимит
    }

    /** Гостю триал не выдаётся: оплатить подписку он всё равно не может. */
    @Test @Order(3)
    void guestSigninDoesNotGrantTrial() {
        var resp = rest.exchange(url("/api/v1/auth/guest"), HttpMethod.POST,
                new HttpEntity<>(Map.of("displayName", "Trial Guest"), jsonHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String guestToken = resp.getBody().get("accessToken").toString();
        var me = rest.exchange(url("/api/v1/me"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(guestToken)), Map.class);
        assertThat(me.getBody().get("plan")).isEqualTo("FREE");
        assertThat(me.getBody().get("trialExpiresAt")).isNull();

        // ...а вот апгрейд до полноценного аккаунта — уже регистрация.
        var upgraded = rest.exchange(url("/api/v1/auth/upgrade-guest"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", UPGRADED_EMAIL, "password", PASSWORD,
                        "displayName", "Upgraded Trial"), authHeaders(guestToken)), Map.class);
        assertThat(upgraded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(planInDb(UPGRADED_EMAIL)).isEqualTo("PRO");
        assertThat(trialInDb(UPGRADED_EMAIL)).isNotNull();
    }

    // ── Истечение ────────────────────────────────────────────────────────────

    /** Триал ещё идёт — джоб проходит мимо. */
    @Test @Order(4)
    void jobLeavesActiveTrialAlone() {
        planExpiryJob.expireTrials();

        assertThat(planInDb(EMAIL)).isEqualTo("PRO");
        assertThat(trialInDb(EMAIL)).isNotNull();
    }

    /** Двигаем срок в прошлое — джоб откатывает на FREE и гасит триал. */
    @Test @Order(5)
    void jobDowngradesExpiredTrial() {
        expireTrialInDb(EMAIL);

        planExpiryJob.expireTrials();

        assertThat(planInDb(EMAIL)).isEqualTo("FREE");
        assertThat(trialInDb(EMAIL)).as("триал отработан и больше не подхватится").isNull();
    }

    /** Повторный прогон ничего не ломает: аккаунт уже не попадает в выборку. */
    @Test @Order(6)
    void jobIsIdempotent() {
        planExpiryJob.expireTrials();

        assertThat(planInDb(EMAIL)).isEqualTo("FREE");
        assertThat(trialInDb(EMAIL)).isNull();
    }

    /** Успел оплатить во время триала — PRO остаётся, джоб его не отбирает. */
    @Test @Order(7)
    void jobKeepsPlanWhenSubscriptionIsActive() {
        UUID userId = userIdOf(EMAIL);
        jdbcTemplate.update("UPDATE app_user SET plan = 'PRO', trial_expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), userId);
        UUID subId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO subscription
                    (id, user_id, plan, status, amount_kopecks, period_months,
                     expires_at, created_at, updated_at)
                VALUES (?, ?, 'PRO', 'ACTIVE', 19900, 1, ?, now(), now())
                """,
                subId, userId, Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)));

        planExpiryJob.expireTrials();

        assertThat(planInDb(EMAIL)).as("оплаченный PRO не отбираем").isEqualTo("PRO");
        assertThat(trialInDb(EMAIL)).as("но триал всё равно закрыт").isNull();

        jdbcTemplate.update("DELETE FROM subscription WHERE id = ?", subId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String planInDb(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT plan FROM app_user WHERE email = ?", String.class, email);
    }

    private Instant trialInDb(String email) {
        Timestamp ts = jdbcTemplate.queryForObject(
                "SELECT trial_expires_at FROM app_user WHERE email = ?", Timestamp.class, email);
        return ts == null ? null : ts.toInstant();
    }

    private UUID userIdOf(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?", UUID.class, email);
    }

    private void expireTrialInDb(String email) {
        jdbcTemplate.update("UPDATE app_user SET trial_expires_at = ? WHERE email = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), email);
    }

    private static org.assertj.core.data.TemporalUnitOffset within(long amount, ChronoUnit unit) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(amount, unit);
    }
}
