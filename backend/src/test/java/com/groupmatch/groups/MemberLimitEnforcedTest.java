package com.groupmatch.groups;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Обратная сторона A2: при включённой монетизации лимит участников работает —
 * и одинаково на обоих путях входа в группу.
 *
 * Раньше {@code InviteService.joinByToken} не проверял лимит вообще, так что
 * ссылка-инвайт обходила ограничение, которое {@code addMember} соблюдал.
 */
@TestPropertySource(properties = "app.features.monetization-enabled=true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MemberLimitEnforcedTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL  = "memberlimit-on-owner@groupmatch-test.io";
    private static final String JOINER_EMAIL = "memberlimit-on-joiner@groupmatch-test.io";
    private static final String PASSWORD = "MemberLimit1!";

    /** FREE.maxMembersPerGroup = 15, одно место занимает владелец. */
    private static final int FREE_LIMIT = 15;

    private String ownerToken;
    private String joinerToken;
    private UUID groupId;
    private final List<UUID> fixtureUsers = new ArrayList<>();

    @BeforeAll
    void setUp() {
        cleanupUser(OWNER_EMAIL);
        cleanupUser(JOINER_EMAIL);
        ownerToken = register(OWNER_EMAIL, "Limit Owner");
        joinerToken = register(JOINER_EMAIL, "Limit Joiner");

        groupId = UUID.fromString(rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("title", "Enforced limit group", "tzId", "UTC"),
                                authHeaders(ownerToken)), Map.class)
                .getBody().get("id").toString());

        for (int i = 0; i < FREE_LIMIT; i++) {
            fixtureUsers.add(insertUser("memberlimit-on-" + i + "-" + UUID.randomUUID()));
        }
    }

    @AfterAll
    void tearDown() {
        fixtureUsers.forEach(id -> jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", id));
        cleanupUser(OWNER_EMAIL);
        cleanupUser(JOINER_EMAIL);
    }

    /** Владелец + 14 участников = ровно лимит. */
    @Test @Order(1)
    void fillsGroupUpToTheLimit() {
        for (int i = 0; i < FREE_LIMIT - 1; i++) {
            assertThat(addMember(fixtureUsers.get(i))).isEqualTo(201);
        }
        assertThat(activeMembers()).isEqualTo(FREE_LIMIT);
    }

    @Test @Order(2)
    void addMemberBeyondLimitReturns402() {
        assertThat(addMember(fixtureUsers.get(FREE_LIMIT - 1))).isEqualTo(402);
    }

    /** Тот же лимит должен действовать и на входе по ссылке-инвайту. */
    @Test @Order(3)
    void joinByInviteBeyondLimitReturns402() {
        String token = rest.exchange(url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("maxUses", 5), authHeaders(ownerToken)), Map.class)
                .getBody().get("token").toString();

        try {
            rest.exchange(url("/api/v1/invites/" + token + "/join"), HttpMethod.POST,
                    new HttpEntity<>(null, authHeaders(joinerToken)), Map.class);
            fail("Expected 402 — ссылка-инвайт не должна обходить лимит участников");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        }
        assertThat(activeMembers()).isEqualTo(FREE_LIMIT);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String register(String email, String displayName) {
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD,
                        "displayName", displayName), jsonHeaders()), Map.class);
        return rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", email, "password", PASSWORD),
                                jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();
    }

    private int addMember(UUID userId) {
        try {
            return rest.exchange(url("/api/v1/groups/" + groupId + "/members"), HttpMethod.POST,
                            new HttpEntity<>(Map.of("userId", userId.toString()), authHeaders(ownerToken)),
                            Map.class)
                    .getStatusCode().value();
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value();
        }
    }

    private int activeMembers() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM grp_member WHERE grp_id = ? AND status = 'ACTIVE'",
                Integer.class, groupId);
        return n == null ? 0 : n;
    }

    private UUID insertUser(String slug) {
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
