package com.groupmatch.groups;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Продолжение A2 на два оставшихся платных лимита: слоты доступности
 * (50 на FREE) и инвайты (3 в час на FREE). Оба проверялись независимо от
 * app.features.monetization-enabled, в отличие от лимита групп в createGroup.
 *
 * Зеркальный кейс с включённым флагом — {@link SlotAndInviteLimitEnforcedTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SlotAndInviteLimitFlagTest extends BaseIntegrationTest {

    private static final String EMAIL = "slotlimit-off@groupmatch-test.io";
    private static final String PASSWORD = "SlotLimit1!";

    /** FREE.maxSlotsPerMember = 50. */
    private static final int OVER_SLOT_LIMIT = 55;
    /** FREE.invitesPerHour = 3. */
    private static final int OVER_INVITE_LIMIT = 6;

    private String accessToken;
    private UUID groupId;

    @BeforeAll
    void setUp() {
        cleanupUser(EMAIL);
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Slot Limit Off"), jsonHeaders()), Map.class);
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

    @Test
    void slotLimitDoesNotApplyWhenMonetizationIsDisabled() {
        for (int i = 0; i < OVER_SLOT_LIMIT; i++) {
            assertThat(addSlot(i))
                    .as("слот #%d при выключенной монетизации", i + 1)
                    .isEqualTo(HttpStatus.CREATED.value());
        }

        Integer stored = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM availability WHERE grp_id = ?", Integer.class, groupId);
        assertThat(stored).isEqualTo(OVER_SLOT_LIMIT);
    }

    @Test
    void inviteRateLimitDoesNotApplyWhenMonetizationIsDisabled() {
        for (int i = 0; i < OVER_INVITE_LIMIT; i++) {
            assertThat(createInvite())
                    .as("инвайт #%d в тот же час при выключенной монетизации", i + 1)
                    .isEqualTo(HttpStatus.CREATED.value());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Слоты не пересекаются по времени, чтобы упереться именно в лимит. */
    private int addSlot(int index) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .plus(index + 1L, ChronoUnit.HOURS);
        return rest.exchange(url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                        new HttpEntity<>(Map.of(
                                "startsAt", start.toString(),
                                "endsAt", start.plus(30, ChronoUnit.MINUTES).toString()
                        ), authHeaders(accessToken)), Map.class)
                .getStatusCode().value();
    }

    private int createInvite() {
        return rest.exchange(url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("maxUses", 5), authHeaders(accessToken)), Map.class)
                .getStatusCode().value();
    }
}
