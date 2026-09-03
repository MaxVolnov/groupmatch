package com.groupmatch.availability;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Правка времени слота и всей серии.
 *
 * Главное здесь то же, что и при создании серии: время настенное. Слот
 * 10:00, переставленный на 11:00, обязан стать 11:00 по местному у каждого
 * слота серии — включая те, что лежат по разные стороны перевода часов.
 * Прибавление одинакового смещения к `Instant` выглядит правильным
 * одиннадцать месяцев в году.
 *
 * Второе — область действия. Правка одного слота выводит его из серии: он
 * перестал соответствовать правилу, и оставлять на нём метку значило бы
 * обещать однородность, которой уже нет.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AvailabilityRetimeTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL = "retime-owner@groupmatch-test.io";
    private static final String MEMBER_EMAIL = "retime-member@groupmatch-test.io";
    private static final String PASSWORD = "RetimeTest1!";

    /** Понедельник. Проверено календарём. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    String ownerToken;
    String memberToken;
    String groupId;

    @BeforeAll
    void setUp() {
        cleanupUser(OWNER_EMAIL);
        cleanupUser(MEMBER_EMAIL);

        ownerToken = signUpAndSignIn(OWNER_EMAIL, "Retime Owner");
        memberToken = signUpAndSignIn(MEMBER_EMAIL, "Retime Member");

        ResponseEntity<Map> group = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Retime Group", "tzId", "UTC"),
                        authHeaders(ownerToken)), Map.class);
        groupId = group.getBody().get("id").toString();

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

    // ── правка одного слота ──────────────────────────────────────────────────

    @Test
    void retimingOneSlotLeavesTheRestOfSeriesAndDropsItsSeriesId() {
        String seriesId = createWeeklySeries("10:00", "12:00");   // четыре вторника
        List<String> ids = seriesSlotIds(seriesId);
        assertThat(ids).hasSize(4);

        ResponseEntity<Map> resp = retime(ids.get(0), "14:00", "15:00", ownerToken);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("seriesId"))
                .as("правленый слот обязан выпасть из серии")
                .isNull();

        // Остальные три не тронуты и всё ещё в серии.
        assertThat(seriesSlotIds(seriesId)).hasSize(3);
        assertThat(startsOf(seriesSlotIds(seriesId)))
                .allMatch((i) -> i.atZone(ZoneId.of("UTC")).getHour() == 10);

        // А у правленого новое время и та же дата.
        Instant moved = startsOf(List.of(ids.get(0))).get(0);
        assertThat(moved.atZone(ZoneId.of("UTC")).getHour()).isEqualTo(14);
        assertThat(moved.atZone(ZoneId.of("UTC")).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void retimingSingleSlotWithoutSeriesWorks() {
        String slotId = addSingleSlot("2026-11-02T09:00:00Z", "2026-11-02T10:00:00Z");

        retime(slotId, "13:00", "14:30", ownerToken);

        Instant start = startsOf(List.of(slotId)).get(0);
        assertThat(start).isEqualTo(Instant.parse("2026-11-02T13:00:00Z"));
    }

    // ── правка серии ─────────────────────────────────────────────────────────

    @Test
    void retimingSeriesMovesEveryySlotAndKeepsDates() {
        String seriesId = createWeeklySeries("10:00", "12:00");
        List<String> ids = seriesSlotIds(seriesId);

        ResponseEntity<Map> resp = retimeSeries(ids.get(0), "11:00", "13:00", ownerToken);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) resp.getBody().get("updatedCount")).intValue()).isEqualTo(4);

        List<Instant> starts = startsOf(seriesSlotIds(seriesId));
        assertThat(starts).hasSize(4);
        // Время у всех новое…
        assertThat(starts).allMatch((i) -> i.atZone(ZoneId.of("UTC")).getHour() == 11);
        // …а даты прежние: вторники остались вторниками.
        assertThat(starts.stream().map((i) -> i.atZone(ZoneId.of("UTC")).toLocalDate()).toList())
                .containsExactly(
                        LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10),
                        LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 24));
    }

    @Test
    void retimingSeriesLeavesOtherSeriesAndStandaloneSlotsAlone() {
        String seriesA = createWeeklySeries("10:00", "12:00");
        String seriesB = createWeeklySeries("15:00", "16:00");
        String standalone = addSingleSlot("2026-12-01T09:00:00Z", "2026-12-01T10:00:00Z");

        retimeSeries(seriesSlotIds(seriesA).get(0), "11:00", "13:00", ownerToken);

        assertThat(startsOf(seriesSlotIds(seriesA)))
                .allMatch((i) -> i.atZone(ZoneId.of("UTC")).getHour() == 11);
        assertThat(startsOf(seriesSlotIds(seriesB)))
                .as("вторая серия того же пользователя тронута быть не должна")
                .allMatch((i) -> i.atZone(ZoneId.of("UTC")).getHour() == 15);
        assertThat(startsOf(List.of(standalone)).get(0))
                .isEqualTo(Instant.parse("2026-12-01T09:00:00Z"));
    }

    /**
     * Главный тест файла.
     *
     * Европа переводит часы в последнее воскресенье октября — в 2026 году это
     * 25 октября. Серия по вторникам 13, 20 и 27 октября лежит по обе стороны
     * перехода. После сдвига на 11:00 локальное время у всех троих одинаково,
     * а абсолютное — нет: 09:00Z, 09:00Z и 10:00Z. Прибавление одинакового
     * смещения к `Instant` дало бы 10:00 по местному у последнего слота.
     */
    @Test
    void retimingSeriesKeepsLocalTimeAcrossDstEnd() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        String seriesId = createSeries("2026-10-13", "2026-10-27", "10:00", "12:00", "Europe/Berlin");
        assertThat(seriesSlotIds(seriesId)).hasSize(3);

        retimeSeries(seriesSlotIds(seriesId).get(0), "11:00", "13:00", ownerToken, "Europe/Berlin");

        List<Instant> starts = startsOf(seriesSlotIds(seriesId));
        for (Instant start : starts) {
            assertThat(start.atZone(berlin).getHour())
                    .as("слот %s по берлинскому времени должен начинаться в 11:00", start)
                    .isEqualTo(11);
        }
        assertThat(starts).containsExactly(
                Instant.parse("2026-10-13T09:00:00Z"),
                Instant.parse("2026-10-20T09:00:00Z"),
                // После перевода те же локальные 11:00 — это уже другой Instant.
                Instant.parse("2026-10-27T10:00:00Z"));
    }

    // ── отказы ───────────────────────────────────────────────────────────────

    @Test
    void retimingSomeoneElsesSlotIsRejected() {
        String slotId = addSingleSlot("2026-11-02T09:00:00Z", "2026-11-02T10:00:00Z");

        try {
            retime(slotId, "13:00", "14:00", memberToken);
            fail("Expected 403");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(startsOf(List.of(slotId)).get(0)).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"));
    }

    @Test
    void endTimeNotAfterStartIsRejectedAndChangesNothing() {
        String slotId = addSingleSlot("2026-11-02T09:00:00Z", "2026-11-02T10:00:00Z");

        try {
            retime(slotId, "13:00", "13:00", ownerToken);
            fail("Expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(startsOf(List.of(slotId)).get(0)).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private ResponseEntity<Map> retime(String slotId, String from, String to, String token) {
        return rest.exchange(url("/api/v1/availability/" + slotId), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("startTime", from, "endTime", to, "timeZone", "UTC"),
                        authHeaders(token)), Map.class);
    }

    private ResponseEntity<Map> retimeSeries(String slotId, String from, String to, String token) {
        return retimeSeries(slotId, from, to, token, "UTC");
    }

    private ResponseEntity<Map> retimeSeries(String slotId, String from, String to, String token, String zone) {
        return rest.exchange(url("/api/v1/availability/" + slotId + "/series"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("startTime", from, "endTime", to, "timeZone", zone),
                        authHeaders(token)), Map.class);
    }

    private String createWeeklySeries(String from, String to) {
        return createSeries(MONDAY.toString(), MONDAY.plusDays(27).toString(), from, to, "UTC");
    }

    private String createSeries(String startDate, String endDate, String from, String to, String zone) {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability/series"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "startDate", startDate,
                        "endDate", endDate,
                        "daysOfWeek", List.of("TUESDAY"),
                        "startTime", from,
                        "endTime", to,
                        "timeZone", zone), authHeaders(ownerToken)), Map.class);
        return resp.getBody().get("seriesId").toString();
    }

    private String addSingleSlot(String startsAt, String endsAt) {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                new HttpEntity<>(Map.of("startsAt", startsAt, "endsAt", endsAt),
                        authHeaders(ownerToken)), Map.class);
        return resp.getBody().get("id").toString();
    }

    private List<String> seriesSlotIds(String seriesId) {
        return jdbcTemplate.queryForList(
                "SELECT id::text FROM availability WHERE series_id = ?::uuid ORDER BY starts_at",
                String.class, seriesId);
    }

    /** Читаем из базы: сериализация Instant — ещё одно место, где время съезжает. */
    private List<Instant> startsOf(List<String> ids) {
        return ids.stream()
                .map((id) -> jdbcTemplate.queryForObject(
                        "SELECT starts_at FROM availability WHERE id = ?::uuid", Timestamp.class, id))
                .map(Timestamp::toInstant)
                .toList();
    }
}
