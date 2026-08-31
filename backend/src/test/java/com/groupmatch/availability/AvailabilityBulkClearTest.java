package com.groupmatch.availability;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Массовая очистка слотов по окну «дни недели × время».
 *
 * Две вещи, ради которых тесты здесь написаны.
 *
 * Первая — окно должно считаться в переданной зоне, а не в UTC. Все слоты
 * заданы через московское время, а в базе лежат как {@code Instant}, поэтому
 * фильтр, спутавший зону с UTC, промахнётся мимо дня недели — и это видно, а
 * не «просто удалилось меньше».
 *
 * Вторая — частично пересекающийся слот не трогается. Это решение, а не
 * недоделка, и без теста его через полгода «починят».
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AvailabilityBulkClearTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL  = "bulkclear-owner@groupmatch-test.io";
    private static final String MEMBER_EMAIL = "bulkclear-member@groupmatch-test.io";
    private static final String PASSWORD     = "BulkClear1!";

    private static final String ZONE = "Europe/Moscow";   // круглый год UTC+3
    private static final String TUESDAY = "2026-11-03";
    private static final String WEDNESDAY = "2026-11-04";

    String ownerToken;
    String memberToken;
    String groupId;

    @BeforeAll
    void setUp() {
        cleanupUser(OWNER_EMAIL);
        cleanupUser(MEMBER_EMAIL);

        ownerToken = signUpAndSignIn(OWNER_EMAIL, "Bulk Owner");
        memberToken = signUpAndSignIn(MEMBER_EMAIL, "Bulk Member");

        ResponseEntity<Map> groupResp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Bulk Clear Group", "tzId", ZONE),
                        authHeaders(ownerToken)), Map.class);
        groupId = groupResp.getBody().get("id").toString();

        ResponseEntity<Map> invite = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                new HttpEntity<>(Map.of("maxUses", 0), authHeaders(ownerToken)), Map.class);
        rest.exchange(url("/api/v1/invites/" + invite.getBody().get("token") + "/join"),
                HttpMethod.POST, new HttpEntity<>(authHeaders(memberToken)), Map.class);
    }

    @BeforeEach
    void clearSlots() {
        jdbcTemplate.update("DELETE FROM availability WHERE grp_id = ?::uuid", groupId);
    }

    @Test
    void slotFullyInsideWindowIsDeleted() {
        // Вторник 11:00–13:00 по Москве — целиком внутри окна 10:00–14:00.
        String slot = addSlot(ownerToken, "2026-11-03T08:00:00Z", "2026-11-03T10:00:00Z");

        assertThat(clear(ownerToken, tuesdayMorningWindow(false))).isEqualTo(1);
        assertThat(exists(slot)).isFalse();
    }

    @Test
    void slotOverlappingWindowPartiallyIsLeftAlone() {
        // Вторник 09:00–15:00 по Москве: окно 10:00–14:00 лежит внутри слота,
        // а не наоборот. Откусывать половину нельзя — слот остаётся целым.
        String slot = addSlot(ownerToken, "2026-11-03T06:00:00Z", "2026-11-03T12:00:00Z");

        assertThat(clear(ownerToken, tuesdayMorningWindow(false))).isZero();
        assertThat(exists(slot)).isTrue();
    }

    @Test
    void slotOnSameDayOutsideWindowIsLeftAlone() {
        // Вторник 16:00–17:00 по Москве — тот день, не то время.
        String slot = addSlot(ownerToken, "2026-11-03T13:00:00Z", "2026-11-03T14:00:00Z");

        assertThat(clear(ownerToken, tuesdayMorningWindow(false))).isZero();
        assertThat(exists(slot)).isTrue();
    }

    @Test
    void slotOnWeekdayOutsideRuleIsLeftAlone() {
        // Среда 11:00–13:00 по Москве — то время, не тот день.
        String slot = addSlot(ownerToken, "2026-11-04T08:00:00Z", "2026-11-04T10:00:00Z");

        assertThat(clear(ownerToken, tuesdayMorningWindow(false))).isZero();
        assertThat(exists(slot)).isTrue();
    }

    @Test
    void otherUsersSlotIsLeftAlone() {
        // Слот участника подходит по всем условиям — и всё равно не наш.
        String mine = addSlot(ownerToken, "2026-11-03T08:00:00Z", "2026-11-03T10:00:00Z");
        String theirs = addSlot(memberToken, "2026-11-03T08:00:00Z", "2026-11-03T10:00:00Z");

        assertThat(clear(ownerToken, tuesdayMorningWindow(false))).isEqualTo(1);
        assertThat(exists(mine)).isFalse();
        assertThat(exists(theirs)).isTrue();
    }

    @Test
    void dryRunCountsWithoutDeleting() {
        String a = addSlot(ownerToken, "2026-11-03T08:00:00Z", "2026-11-03T10:00:00Z");
        String b = addSlot(ownerToken, "2026-11-03T10:00:00Z", "2026-11-03T11:00:00Z");

        int predicted = clear(ownerToken, tuesdayMorningWindow(true));
        assertThat(predicted).isEqualTo(2);
        assertThat(exists(a)).isTrue();
        assertThat(exists(b)).isTrue();

        // Предсказание обязано совпасть с фактом, иначе «посмотреть перед
        // удалением» — бесполезная кнопка.
        assertThat(clear(ownerToken, tuesdayMorningWindow(false))).isEqualTo(predicted);
        assertThat(exists(a)).isFalse();
        assertThat(exists(b)).isFalse();
    }

    /**
     * Слот, который во вторник по Москве, но в понедельник по UTC.
     *
     * 01:00–02:00 MSK — это 22:00–23:00 предыдущих суток по Гринвичу. Если бы
     * день недели считался в UTC, фильтр увидел бы понедельник и слот бы
     * уцелел. Это единственная проверка, которую нельзя пройти «случайно».
     */
    @Test
    void weekdayIsResolvedInRequestedZoneNotUtc() {
        String slot = addSlot(ownerToken, "2026-11-02T22:00:00Z", "2026-11-02T23:00:00Z");

        int deleted = clear(ownerToken, Map.of(
                "daysOfWeek", List.of("TUESDAY"),
                "startTime", "00:00",
                "endTime",   "03:00",
                "fromDate",  TUESDAY,
                "toDate",    TUESDAY,
                "timeZone",  ZONE,
                "dryRun",    false));

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(slot)).isFalse();
    }

    @Test
    void slotOutsideDateRangeIsLeftAlone() {
        // Тот же вторник в следующем месяце — правило по дню недели совпадает,
        // диапазон дат нет.
        String slot = addSlot(ownerToken, "2026-12-01T08:00:00Z", "2026-12-01T10:00:00Z");

        assertThat(clear(ownerToken, tuesdayMorningWindow(false))).isZero();
        assertThat(exists(slot)).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Вторники 10:00–14:00 по Москве в неделе со 2 по 4 ноября. */
    private Map<String, Object> tuesdayMorningWindow(boolean dryRun) {
        return Map.of(
                "daysOfWeek", List.of("TUESDAY"),
                "startTime", "10:00",
                "endTime",   "14:00",
                "fromDate",  TUESDAY,
                "toDate",    WEDNESDAY,
                "timeZone",  ZONE,
                "dryRun",    dryRun);
    }

    private int clear(String token, Map<String, Object> body) {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability/bulk"), HttpMethod.DELETE,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("deletedCount")).intValue();
    }

    private String signUpAndSignIn(String email, String displayName) {
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD,
                        "displayName", displayName), jsonHeaders()), Map.class);
        ResponseEntity<Map> signin = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD),
                        jsonHeaders()), Map.class);
        return signin.getBody().get("accessToken").toString();
    }

    private String addSlot(String token, String startsAt, String endsAt) {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                new HttpEntity<>(Map.of("startsAt", startsAt, "endsAt", endsAt),
                        authHeaders(token)), Map.class);
        return resp.getBody().get("id").toString();
    }

    private boolean exists(String slotId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM availability WHERE id = ?::uuid", Integer.class, slotId) > 0;
    }
}
