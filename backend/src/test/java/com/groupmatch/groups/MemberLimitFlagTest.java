package com.groupmatch.groups;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2: лимит участников (15 на FREE) применялся независимо от флага монетизации,
 * в отличие от лимита групп в createGroup. При MONETIZATION_ENABLED=false
 * платных лимитов быть не должно.
 *
 * Зеркальный кейс с включённым флагом — {@link MemberLimitEnforcedTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MemberLimitFlagTest extends BaseIntegrationTest {

    static final String OWNER_EMAIL = "memberlimit-owner@groupmatch-test.io";
    static final String PASSWORD = "MemberLimit1!";

    /** FREE.maxMembersPerGroup = 15, владелец уже занимает одно место. */
    static final int OVER_FREE_LIMIT = 20;

    String ownerToken;
    UUID groupId;
    final List<UUID> fixtureUsers = new ArrayList<>();

    @BeforeAll
    void setUp() {
        cleanupUser(OWNER_EMAIL);
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", PASSWORD,
                        "displayName", "Limit Owner"), jsonHeaders()), Map.class);
        ownerToken = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", PASSWORD),
                                jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();

        groupId = UUID.fromString(rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("title", "Member limit group", "tzId", "UTC"),
                                authHeaders(ownerToken)), Map.class)
                .getBody().get("id").toString());

        // Участников заводим напрямую в БД: Argon2 на каждый signup стоит дороже,
        // чем весь остальной тест, а нам важен только счётчик участников.
        for (int i = 0; i < OVER_FREE_LIMIT; i++) {
            fixtureUsers.add(insertUser("memberlimit-" + i + "-" + UUID.randomUUID()));
        }
    }

    @AfterAll
    void tearDown() {
        fixtureUsers.forEach(id -> jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", id));
        cleanupUser(OWNER_EMAIL);
    }

    @Test
    void memberLimitDoesNotApplyWhenMonetizationIsDisabled() {
        for (int i = 0; i < OVER_FREE_LIMIT; i++) {
            var resp = rest.exchange(url("/api/v1/groups/" + groupId + "/members"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("userId", fixtureUsers.get(i).toString()),
                            authHeaders(ownerToken)), Map.class);
            assertThat(resp.getStatusCode())
                    .as("участник #%d при выключенной монетизации", i + 2)
                    .isEqualTo(HttpStatus.CREATED);
        }

        // Владелец + 20 участников — все на месте, 402 нигде не прилетело.
        Integer active = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM grp_member WHERE grp_id = ? AND status = 'ACTIVE'",
                Integer.class, groupId);
        assertThat(active).isEqualTo(OVER_FREE_LIMIT + 1);
    }

    UUID insertUser(String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, email, password_hash, display_name, tz_id, locale, plan, role,
                     is_guest, is_banned, is_email_verified, is_blocked,
                     created_at, updated_at, last_activity_at)
                VALUES (?, ?, 'x', ?, 'UTC', 'ru', 'FREE', 'USER',
                        false, false, true, false, ?, ?, ?)
                """,
                id, slug + "@groupmatch-test.io", "Member " + slug.substring(0, 14),
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
        return id;
    }
}
