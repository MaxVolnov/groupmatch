package com.groupmatch.meetings;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * .ics-подписка на группу: постоянный фид со всеми будущими встречами.
 *
 * Доступ по токену в URL, без JWT — календарные клиенты Authorization не шлют.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GroupCalendarFeedTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL = "calendar-owner@groupmatch-test.io";
    private static final String OUTSIDER_EMAIL = "calendar-outsider@groupmatch-test.io";
    private static final String PASSWORD = "Calendar1!";

    private static final String FUTURE_TITLE = "Планёрка; на следующей неделе";
    private static final String PAST_TITLE = "Прошедшая встреча";

    private String ownerToken;
    private String outsiderToken;
    private UUID groupId;
    private String feedUrl;
    private String token;

    @BeforeAll
    void setUp() {
        cleanupUser(OWNER_EMAIL);
        cleanupUser(OUTSIDER_EMAIL);
        ownerToken = register(OWNER_EMAIL, "Calendar Owner");
        outsiderToken = register(OUTSIDER_EMAIL, "Calendar Outsider");

        groupId = UUID.fromString(rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("title", "Календарная группа", "tzId", "UTC"),
                                authHeaders(ownerToken)), Map.class)
                .getBody().get("id").toString());

        Instant future = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        createMeeting(FUTURE_TITLE, future, future.plus(1, ChronoUnit.HOURS));
        createMeeting("Ещё одна будущая", future.plus(1, ChronoUnit.DAYS),
                future.plus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES));

        // Прошедшую заводим напрямую в БД: API не даёт создать встречу в прошлом.
        insertPastMeeting();
    }

    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM meeting WHERE grp_id = ?", groupId);
        cleanupUser(OWNER_EMAIL);
        cleanupUser(OUTSIDER_EMAIL);
    }

    // ── Ссылка на подписку ───────────────────────────────────────────────────

    @Test @Order(1)
    void memberGetsSubscriptionLinks() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/calendar-subscription"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        feedUrl = resp.getBody().get("url").toString();
        String webcal = resp.getBody().get("webcalUrl").toString();

        assertThat(feedUrl).contains("/api/v1/groups/" + groupId + "/calendar.ics?token=");
        assertThat(webcal).startsWith("webcal://");
        assertThat(webcal.replaceFirst("^webcal://", ""))
                .as("webcal-ссылка — тот же URL, другая схема")
                .isEqualTo(feedUrl.replaceFirst("^https?://", ""));
        assertThat(resp.getBody().get("refreshMinutes")).isEqualTo(60);

        token = feedUrl.substring(feedUrl.indexOf("token=") + 6);
        assertThat(token).hasSize(48); // 24 байта в hex
    }

    /** Посторонний ссылку на подписку получить не должен. */
    @Test @Order(2)
    void outsiderCannotGetSubscriptionLink() {
        try {
            rest.exchange(url("/api/v1/groups/" + groupId + "/calendar-subscription"), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(outsiderToken)), Map.class);
            fail("Expected 403");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── Сам фид ──────────────────────────────────────────────────────────────

    @Test @Order(3)
    void feedIsServedWithoutAuthorizationHeader() {
        ResponseEntity<String> resp = getFeed(token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getFirst("Content-Type")).startsWith("text/calendar");
    }

    @Test @Order(4)
    void feedIsSyntacticallyValid() {
        String ics = getFeed(token).getBody();
        IcsAssert.assertParsable(ics);

        assertThat(ics).contains("VERSION:2.0");
        assertThat(ics).contains("PRODID:-//GroupMatch//GroupMatch//EN");
        assertThat(ics).contains("METHOD:PUBLISH");
        assertThat(ics).contains("REFRESH-INTERVAL;VALUE=DURATION:PT60M");
        assertThat(ics).contains("X-WR-CALNAME:Календарная группа");
    }

    @Test @Order(5)
    void feedContainsAllUpcomingMeetingsAndNoPastOnes() {
        String ics = getFeed(token).getBody();

        assertThat(IcsAssert.eventCount(ics)).as("две будущие встречи").isEqualTo(2);
        // ; в названии экранируется по RFC 5545
        assertThat(ics).contains("SUMMARY:Планёрка\\; на следующей неделе");
        assertThat(ics).contains("SUMMARY:Ещё одна будущая");
        assertThat(ics).as("прошедшая встреча в подписку не попадает")
                .doesNotContain(PAST_TITLE);
    }

    /** Фид живой: добавили встречу — она появилась в том же URL. */
    @Test @Order(6)
    void feedReflectsNewlyCreatedMeetings() {
        assertThat(IcsAssert.eventCount(getFeed(token).getBody())).isEqualTo(2);

        Instant start = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        createMeeting("Добавлена после подписки", start, start.plus(1, ChronoUnit.HOURS));

        String ics = getFeed(token).getBody();
        assertThat(IcsAssert.eventCount(ics)).isEqualTo(3);
        assertThat(ics).contains("SUMMARY:Добавлена после подписки");
    }

    // ── Доступ ───────────────────────────────────────────────────────────────

    @Test @Order(7)
    void feedRejectsWrongOrMissingToken() {
        assertThat(feedStatus("0".repeat(48))).as("чужой токен").isEqualTo(404);
        assertThat(feedStatus(null)).as("токена нет вовсе").isEqualTo(404);
        assertThat(feedStatus("")).as("пустой токен").isEqualTo(404);
        assertThat(feedStatus(token.substring(0, 47))).as("обрезанный токен").isEqualTo(404);
    }

    /** Несуществующая группа и неверный токен неотличимы снаружи. */
    @Test @Order(8)
    void unknownGroupLooksTheSameAsBadToken() {
        try {
            rest.exchange(url("/api/v1/groups/" + UUID.randomUUID() + "/calendar.ics?token=" + token),
                    HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
            fail("Expected 404");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<String> getFeed(String feedToken) {
        return rest.exchange(url("/api/v1/groups/" + groupId + "/calendar.ics?token=" + feedToken),
                HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
    }

    private int feedStatus(String feedToken) {
        String path = feedToken == null
                ? "/api/v1/groups/" + groupId + "/calendar.ics"
                : "/api/v1/groups/" + groupId + "/calendar.ics?token=" + feedToken;
        try {
            return rest.exchange(url(path), HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()), String.class).getStatusCode().value();
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value();
        }
    }

    private String register(String email, String displayName) {
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD,
                        "displayName", displayName), jsonHeaders()), Map.class);
        return rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", email, "password", PASSWORD), jsonHeaders()),
                        Map.class)
                .getBody().get("accessToken").toString();
    }

    private void createMeeting(String title, Instant startsAt, Instant endsAt) {
        rest.exchange(url("/api/v1/groups/" + groupId + "/meetings"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "title", title,
                        "startsAt", startsAt.toString(),
                        "endsAt", endsAt.toString()
                ), authHeaders(ownerToken)), Map.class);
    }

    private void insertPastMeeting() {
        UUID creatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?", UUID.class, OWNER_EMAIL);
        jdbcTemplate.update("""
                INSERT INTO meeting (id, grp_id, creator_id, title, starts_at, ends_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                """,
                UUID.randomUUID(), groupId, creatorId, PAST_TITLE,
                java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)),
                java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)));
    }
}
