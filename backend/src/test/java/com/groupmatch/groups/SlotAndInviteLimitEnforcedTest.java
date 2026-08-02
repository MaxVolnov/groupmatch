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
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Обратная сторона: при включённой монетизации лимиты слотов и инвайтов
 * работают ровно так же, как до выноса под флаг — регрессии нет.
 *
 * Свойство контекста совпадает с {@link MemberLimitEnforcedTest}, поэтому
 * Spring переиспользует уже поднятый контекст, а не запускает ещё один.
 */
@TestPropertySource(properties = "app.features.monetization-enabled=true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SlotAndInviteLimitEnforcedTest extends BaseIntegrationTest {

    private static final String EMAIL = "slotlimit-on@groupmatch-test.io";
    private static final String PASSWORD = "SlotLimit1!";

    /** FREE.maxSlotsPerMember = 50. */
    private static final int SLOT_LIMIT = 50;
    /** FREE.invitesPerHour = 3. */
    private static final int INVITE_LIMIT = 3;

    private String accessToken;
    private UUID groupId;

    @BeforeAll
    void setUp() {
        cleanupUser(EMAIL);
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Slot Limit On"), jsonHeaders()), Map.class);
        // Лимиты здесь — FREE-шные, поэтому триальный PRO снимаем до signin.
        downgradeToFree(EMAIL);
        accessToken = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();

        groupId = UUID.fromString(rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("title", "Slot limit group", "tzId", "UTC"),
                                authHeaders(accessToken)), Map.class)
                .getBody().get("id").toString());
    }

    @AfterAll
    void tearDown() {
        cleanupUser(EMAIL);
    }

    @Test @Order(1)
    void fillsSlotsUpToTheLimit() {
        for (int i = 0; i < SLOT_LIMIT; i++) {
            assertThat(addSlot(i)).as("слот #%d", i + 1).isEqualTo(201);
        }
    }

    @Test @Order(2)
    void slotBeyondLimitReturns402() {
        assertThat(addSlot(SLOT_LIMIT)).as("51-й слот").isEqualTo(402);

        Integer stored = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM availability WHERE grp_id = ?", Integer.class, groupId);
        assertThat(stored).isEqualTo(SLOT_LIMIT);
    }

    @Test @Order(3)
    void inviteRateLimitStillApplies() {
        for (int i = 0; i < INVITE_LIMIT; i++) {
            assertThat(createInvite()).as("инвайт #%d", i + 1).isEqualTo(201);
        }
        assertThat(createInvite()).as("4-й инвайт в тот же час").isEqualTo(402);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int addSlot(int index) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .plus(index + 1L, ChronoUnit.HOURS);
        try {
            return rest.exchange(url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                            new HttpEntity<>(Map.of(
                                    "startsAt", start.toString(),
                                    "endsAt", start.plus(30, ChronoUnit.MINUTES).toString()
                            ), authHeaders(accessToken)), Map.class)
                    .getStatusCode().value();
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value();
        }
    }

    private int createInvite() {
        try {
            return rest.exchange(url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                            new HttpEntity<>(Map.of("maxUses", 5), authHeaders(accessToken)), Map.class)
                    .getStatusCode().value();
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value();
        }
    }
}
