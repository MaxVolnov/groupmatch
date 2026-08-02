package com.groupmatch.auth;

import com.groupmatch.BaseIntegrationTest;
import com.groupmatch.config.GuestCleanupJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Гостевой TTL считается от последней активности, а не от даты создания.
 * Регрессия P0: старый запрос удалял по created_at, из-за чего гость,
 * заходящий каждый день, терял все данные ровно через N дней после регистрации.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GuestCleanupTest extends BaseIntegrationTest {

    @Autowired
    GuestCleanupJob guestCleanupJob;

    /** Старый (создан 200 дней назад), но активный вчера — должен выжить. */
    @Test
    void activeGuestIsKept() {
        UUID id = insertGuest(daysAgo(200), daysAgo(1));
        guestCleanupJob.cleanupExpiredGuestAccounts();
        assertThat(exists(id)).as("гость с недавней активностью не удаляется").isTrue();
        delete(id);
    }

    /** Граница: активность на 89-й день — ещё внутри окна в 90 дней. */
    @Test
    void guestActiveOnDay89IsKept() {
        UUID id = insertGuest(daysAgo(365), daysAgo(89));
        guestCleanupJob.cleanupExpiredGuestAccounts();
        assertThat(exists(id)).as("активность 89 дней назад — аккаунт живёт").isTrue();
        delete(id);
    }

    /** За границей: 91 день без активности — удаляется. */
    @Test
    void guestInactiveOver90DaysIsDeleted() {
        UUID id = insertGuest(daysAgo(365), daysAgo(91));
        guestCleanupJob.cleanupExpiredGuestAccounts();
        assertThat(exists(id)).as("91 день без активности — аккаунт удалён").isFalse();
    }

    /** Обычный (не гостевой) аккаунт джоб не трогает, даже если он давно заброшен. */
    @Test
    void regularUserIsNeverDeleted() {
        UUID id = insertUser(daysAgo(365), daysAgo(365), false);
        guestCleanupJob.cleanupExpiredGuestAccounts();
        assertThat(exists(id)).as("не-гостевые аккаунты джоб не удаляет").isTrue();
        delete(id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID insertGuest(Instant createdAt, Instant lastActivityAt) {
        return insertUser(createdAt, lastActivityAt, true);
    }

    private UUID insertUser(Instant createdAt, Instant lastActivityAt, boolean guest) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, email, password_hash, display_name, tz_id, locale, plan, role,
                     is_guest, is_banned, is_email_verified, is_blocked,
                     created_at, updated_at, last_activity_at)
                VALUES (?, ?, 'x', 'Cleanup Fixture', 'UTC', 'ru', 'FREE', 'USER',
                        ?, false, false, false, ?, ?, ?)
                """,
                id,
                "cleanup-" + id + "@groupmatch-test.io",
                guest,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                Timestamp.from(lastActivityAt));
        return id;
    }

    private boolean exists(UUID id) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", id);
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
