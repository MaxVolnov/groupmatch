package com.groupmatch.meetings;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MeetingTest extends BaseIntegrationTest {

    private static final String EMAIL    = "meetingtest@groupmatch-test.io";
    private static final String PASSWORD = "MeetTest1!";

    String accessToken;
    String groupId;
    String meetingId;

    @BeforeAll
    void setUp() {
        cleanupUser(EMAIL);

        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Meeting Tester"), jsonHeaders()), Map.class);

        ResponseEntity<Map> signinResp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                        jsonHeaders()), Map.class);
        accessToken = signinResp.getBody().get("accessToken").toString();

        ResponseEntity<Map> groupResp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Meeting Test Group", "tzId", "UTC"),
                        authHeaders(accessToken)), Map.class);
        groupId = groupResp.getBody().get("id").toString();
    }

    // ── 1. create meeting → 201 ───────────────────────────────────────────────

    @Test @Order(1)
    void createMeetingSucceeds() {
        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        var body = Map.of(
                "title", "Team Sync",
                "startsAt", startsAt.toString(),
                "endsAt", startsAt.plus(1, ChronoUnit.HOURS).toString()
        );
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/meetings"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("title")).isEqualTo("Team Sync");
        meetingId = resp.getBody().get("id").toString();
        assertThat(meetingId).isNotBlank();
    }

    // ── 2. list meetings → contains created ───────────────────────────────────

    @Test @Order(2)
    @SuppressWarnings("unchecked")
    void listMeetingsContainsCreated() {
        ResponseEntity<List> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/meetings"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();
        assertThat(resp.getBody())
                .anyMatch(item -> meetingId.equals(((Map<?, ?>) item).get("id").toString()));
    }

    // ── 3. get meeting → 200 ─────────────────────────────────────────────────

    @Test @Order(3)
    void getMeetingSucceeds() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/meetings/" + meetingId), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("id").toString()).isEqualTo(meetingId);
        assertThat(resp.getBody().get("title")).isEqualTo("Team Sync");
    }

    // ── 4. update meeting → 200 ───────────────────────────────────────────────

    @Test @Order(4)
    void updateMeetingSucceeds() {
        Instant startsAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        var body = Map.of(
                "title", "Team Sync Updated",
                "startsAt", startsAt.toString(),
                "endsAt", startsAt.plus(2, ChronoUnit.HOURS).toString()
        );
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/meetings/" + meetingId), HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("title")).isEqualTo("Team Sync Updated");
    }

    // ── 5. export ICS → 200, content-type text/calendar ─────────────────────

    @Test @Order(5)
    void exportIcsSucceeds() {
        ResponseEntity<String> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/meetings/" + meetingId + "/export.ics"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().toString()).contains("text/calendar");
        assertThat(resp.getBody()).contains("BEGIN:VCALENDAR");
    }

    // ── 6. delete meeting → 204 ──────────────────────────────────────────────

    @Test @Order(6)
    void deleteMeetingSucceeds() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/meetings/" + meetingId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(accessToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
