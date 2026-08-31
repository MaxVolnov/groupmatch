package com.groupmatch.availability;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Повторяющиеся серии и удаление с областью действия.
 *
 * Главное здесь — не арифметика количества слотов, а то, что разворачивание
 * идёт по локальному настенному времени. Ошибка «прибавить сутки к Instant»
 * выглядит правильной одиннадцать месяцев в году и ломается в неделю
 * перевода часов, когда сутки длятся 23 или 25 часов. Тесты
 * {@link #seriesKeepsLocalTimeAcrossDstEnd()} и
 * {@link #seriesInMoscowHasNoDstShift()} написаны именно под неё.
 *
 * Даты зафиксированы, а не посчитаны от {@code now()}: тест про перевод часов
 * бессмыслен, если диапазон уезжает вместе с датой прогона.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AvailabilitySeriesTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL  = "series-owner@groupmatch-test.io";
    private static final String MEMBER_EMAIL = "series-member@groupmatch-test.io";
    private static final String PASSWORD     = "SeriesTest1!";

    /** Понедельник. Проверено календарём, не выведено из today(). */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    String ownerToken;
    String memberToken;
    String groupId;

    @BeforeAll
    void setUp() {
        cleanupUser(OWNER_EMAIL);
        cleanupUser(MEMBER_EMAIL);

        ownerToken = signUpAndSignIn(OWNER_EMAIL, "Series Owner");
        memberToken = signUpAndSignIn(MEMBER_EMAIL, "Series Member");

        ResponseEntity<Map> groupResp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Series Group", "tzId", "UTC"),
                        authHeaders(ownerToken)), Map.class);
        groupId = groupResp.getBody().get("id").toString();

        // Второй участник — обычный member, не владелец. Нужен ровно для одной
        // проверки: чужой слот удалить нельзя.
        ResponseEntity<Map> invite = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                new HttpEntity<>(Map.of("maxUses", 0), authHeaders(ownerToken)), Map.class);
        rest.exchange(url("/api/v1/invites/" + invite.getBody().get("token") + "/join"),
                HttpMethod.POST, new HttpEntity<>(authHeaders(memberToken)), Map.class);
    }

    /** Слоты между тестами не копятся: каждый тест считает свои. */
    @BeforeEach
    void clearSlots() {
        jdbcTemplate.update("DELETE FROM availability WHERE grp_id = ?::uuid", groupId);
    }

    // ── создание серии ────────────────────────────────────────────────────────

    @Test
    void weeklySeriesOverFourWeeksCreatesFourSlots() {
        // Понедельник + 27 дней = воскресенье через четыре недели; вторников
        // в этом окне ровно четыре.
        Map body = createSeries(ownerToken, Map.of(
                "startDate", MONDAY.toString(),
                "endDate",   MONDAY.plusDays(27).toString(),
                "daysOfWeek", List.of("TUESDAY"),
                "startTime", "10:00",
                "endTime",   "12:00",
                "timeZone",  "UTC"));

        assertThat(body.get("createdCount")).isEqualTo(4);

        String seriesId = body.get("seriesId").toString();
        assertThat(seriesStarts(seriesId)).hasSize(4);
        // Один seriesId на всю серию — иначе удаление серии целиком не работает.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT series_id) FROM availability WHERE grp_id = ?::uuid",
                Integer.class, groupId)).isEqualTo(1);
    }

    @Test
    void twoDaysPerWeekOverTwoWeeksCreatesFourSlots() {
        Map body = createSeries(ownerToken, Map.of(
                "startDate", MONDAY.toString(),
                "endDate",   MONDAY.plusDays(13).toString(),
                "daysOfWeek", List.of("MONDAY", "WEDNESDAY"),
                "startTime", "09:00",
                "endTime",   "10:30",
                "timeZone",  "UTC"));

        assertThat(body.get("createdCount")).isEqualTo(4);
    }

    /**
     * Главный тест пункта про время.
     *
     * Европа переводит часы в последнее воскресенье октября — в 2026 году это
     * 25 октября. Вторники 20 и 27 октября лежат по разные стороны перехода:
     * локальные 10:00 в Берлине — это 08:00 UTC до перевода и 09:00 UTC после.
     *
     * Отсюда две проверки, и обе обязательные. Локальное время у всех слотов
     * равно 10:00 — это то, что человек вводил. Абсолютное время при этом
     * разное — это доказательство, что шаг делался по календарю, а не
     * прибавлением 168 часов: при шаге по Instant локальное время после
     * перевода стало бы 09:00.
     */
    @Test
    void seriesKeepsLocalTimeAcrossDstEnd() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");

        Map body = createSeries(ownerToken, Map.of(
                "startDate", "2026-10-13",
                "endDate",   "2026-10-27",
                "daysOfWeek", List.of("TUESDAY"),
                "startTime", "10:00",
                "endTime",   "12:00",
                "timeZone",  "Europe/Berlin"));

        assertThat(body.get("createdCount")).isEqualTo(3);
        List<Instant> starts = seriesStarts(body.get("seriesId").toString());
        assertThat(starts).hasSize(3);

        for (Instant start : starts) {
            ZonedDateTime local = start.atZone(berlin);
            assertThat(local.getHour())
                    .as("слот %s по берлинскому времени должен начинаться в 10:00", start)
                    .isEqualTo(10);
            assertThat(local.getMinute()).isZero();
        }

        // 13 и 20 октября — летнее время (+02:00), 27 октября — зимнее (+01:00).
        assertThat(starts.get(0)).isEqualTo(Instant.parse("2026-10-13T08:00:00Z"));
        assertThat(starts.get(1)).isEqualTo(Instant.parse("2026-10-20T08:00:00Z"));
        assertThat(starts.get(2))
                .as("после перевода часов те же локальные 10:00 — это уже другой Instant")
                .isEqualTo(Instant.parse("2026-10-27T09:00:00Z"));
    }

    /**
     * Тот же диапазон в Europe/Moscow — и здесь смещение обязано остаться
     * прежним.
     *
     * Россия отменила сезонный перевод часов в 2011 году, Москва круглый год
     * UTC+3. Проверка написана как зеркало предыдущей: она ловит обратную
     * ошибку — «поправку на переход», применённую к зоне, которая не
     * переходит.
     */
    @Test
    void seriesInMoscowHasNoDstShift() {
        Map body = createSeries(ownerToken, Map.of(
                "startDate", "2026-10-13",
                "endDate",   "2026-10-27",
                "daysOfWeek", List.of("TUESDAY"),
                "startTime", "10:00",
                "endTime",   "12:00",
                "timeZone",  "Europe/Moscow"));

        List<Instant> starts = seriesStarts(body.get("seriesId").toString());
        assertThat(starts).containsExactly(
                Instant.parse("2026-10-13T07:00:00Z"),
                Instant.parse("2026-10-20T07:00:00Z"),
                Instant.parse("2026-10-27T07:00:00Z"));
    }

    @Test
    void seriesExceedingSlotCapIsRejectedAndSavesNothing() {
        // Все семь дней недели на 201 день подряд — 201 слот при потолке 200.
        try {
            createSeries(ownerToken, Map.of(
                    "startDate", MONDAY.toString(),
                    "endDate",   MONDAY.plusDays(200).toString(),
                    "daysOfWeek", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                                          "FRIDAY", "SATURDAY", "SUNDAY"),
                    "startTime", "10:00",
                    "endTime",   "12:00",
                    "timeZone",  "UTC"));
            fail("Expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // Сколько получилось бы — в сообщении: без этого числа сузить
            // диапазон можно только наугад.
            assertThat(e.getResponseBodyAsString()).contains("201");
        }
        assertThat(slotCount()).isZero();
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        expectBadRequest(Map.of(
                "startDate", MONDAY.toString(),
                "endDate",   MONDAY.minusDays(1).toString(),
                "daysOfWeek", List.of("TUESDAY"),
                "startTime", "10:00",
                "endTime",   "12:00",
                "timeZone",  "UTC"));
    }

    @Test
    void unknownTimeZoneIsRejected() {
        expectBadRequest(Map.of(
                "startDate", MONDAY.toString(),
                "endDate",   MONDAY.plusDays(27).toString(),
                "daysOfWeek", List.of("TUESDAY"),
                "startTime", "10:00",
                "endTime",   "12:00",
                "timeZone",  "Mars/Olympus_Mons"));
    }

    // ── удаление с областью действия ─────────────────────────────────────────

    @Test
    void deleteSingleLeavesRestOfSeries() {
        String seriesId = createWeeklySeries(ownerToken, "10:00", "12:00");
        String victim = seriesSlotIds(seriesId).get(0);

        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/availability/" + victim + "?scope=single"),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(ownerToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(seriesSlotIds(seriesId)).hasSize(3);
    }

    @Test
    void deleteSeriesRemovesWholeSeriesOnly() {
        String seriesA = createWeeklySeries(ownerToken, "10:00", "12:00");
        String seriesB = createWeeklySeries(ownerToken, "15:00", "16:00");

        rest.exchange(url("/api/v1/availability/" + seriesSlotIds(seriesA).get(0) + "?scope=series"),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(ownerToken)), Void.class);

        assertThat(seriesSlotIds(seriesA)).isEmpty();
        assertThat(seriesSlotIds(seriesB))
                .as("вторая серия того же пользователя тронута быть не должна")
                .hasSize(4);
    }

    @Test
    void deleteSeriesOnStandaloneSlotRemovesOnlyIt() {
        String seriesId = createWeeklySeries(ownerToken, "10:00", "12:00");
        String standalone = addSingleSlot(ownerToken, "2026-12-01T09:00:00Z", "2026-12-01T10:00:00Z");

        rest.exchange(url("/api/v1/availability/" + standalone + "?scope=series"),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(ownerToken)), Void.class);

        assertThat(slotExists(standalone)).isFalse();
        assertThat(seriesSlotIds(seriesId)).hasSize(4);
    }

    @Test
    void deletingSomeoneElsesSlotIsRejected() {
        String ownersSlot = addSingleSlot(ownerToken, "2026-12-02T09:00:00Z", "2026-12-02T10:00:00Z");

        try {
            rest.exchange(url("/api/v1/availability/" + ownersSlot),
                    HttpMethod.DELETE, new HttpEntity<>(authHeaders(memberToken)), Void.class);
            fail("Expected 403");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(slotExists(ownersSlot)).isTrue();
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

    private Map createSeries(String token, Map<String, Object> body) {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability/series"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private String createWeeklySeries(String token, String startTime, String endTime) {
        return createSeries(token, Map.of(
                "startDate", MONDAY.toString(),
                "endDate",   MONDAY.plusDays(27).toString(),
                "daysOfWeek", List.of("TUESDAY"),
                "startTime", startTime,
                "endTime",   endTime,
                "timeZone",  "UTC")).get("seriesId").toString();
    }

    private String addSingleSlot(String token, String startsAt, String endsAt) {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                new HttpEntity<>(Map.of("startsAt", startsAt, "endsAt", endsAt),
                        authHeaders(token)), Map.class);
        return resp.getBody().get("id").toString();
    }

    private void expectBadRequest(Map<String, Object> body) {
        try {
            rest.exchange(url("/api/v1/groups/" + groupId + "/availability/series"),
                    HttpMethod.POST, new HttpEntity<>(body, authHeaders(ownerToken)), Map.class);
            fail("Expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(slotCount()).isZero();
    }

    /**
     * Читаем из базы, а не из ответа API: сериализация {@code Instant} — это
     * ещё одно место, где время может незаметно съехать, и подмешивать её в
     * проверку про перевод часов не стоит.
     */
    private List<Instant> seriesStarts(String seriesId) {
        return jdbcTemplate.queryForList(
                        "SELECT starts_at FROM availability WHERE series_id = ?::uuid ORDER BY starts_at",
                        Timestamp.class, seriesId)
                .stream().map(Timestamp::toInstant).toList();
    }

    private List<String> seriesSlotIds(String seriesId) {
        return jdbcTemplate.queryForList(
                "SELECT id::text FROM availability WHERE series_id = ?::uuid ORDER BY starts_at",
                String.class, seriesId);
    }

    private boolean slotExists(String slotId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM availability WHERE id = ?::uuid", Integer.class, slotId) > 0;
    }

    private int slotCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM availability WHERE grp_id = ?::uuid", Integer.class, groupId);
    }
}
