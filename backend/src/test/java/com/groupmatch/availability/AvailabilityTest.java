package com.groupmatch.availability;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AvailabilityTest extends BaseIntegrationTest {

    private static final String EMAIL    = "availtest@groupmatch-test.io";
    private static final String PASSWORD = "AvailTest1!";

    String accessToken;
    String groupId;
    String slotId;

    @BeforeAll
    void setUp() {
        cleanupUser(EMAIL);

        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Avail Tester"), jsonHeaders()), Map.class);

        ResponseEntity<Map> signinResp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                        jsonHeaders()), Map.class);
        accessToken = signinResp.getBody().get("accessToken").toString();

        ResponseEntity<Map> groupResp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Avail Group", "tzId", "UTC"),
                        authHeaders(accessToken)), Map.class);
        groupId = groupResp.getBody().get("id").toString();
    }

    // 1. Add slot → 201
    @Test @Order(1)
    void addSlotSucceeds() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        Instant end   = start.plus(2, ChronoUnit.HOURS);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "startsAt", start.toString(),
                        "endsAt",   end.toString()
                ), authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("id");
        slotId = resp.getBody().get("id").toString();
    }

    // 2. Get my slots → contains created slot
    @Test @Order(2)
    void getMySlotsContainsCreated() {
        ResponseEntity<List> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability/my"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();
    }

    // 3. Heatmap returns non-null response
    @Test @Order(3)
    void getHeatmapSucceeds() {
        Instant from = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant to   = from.plus(7, ChronoUnit.DAYS);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability/heatmap"
                        + "?from=" + from + "&to=" + to + "&granularityMinutes=30"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("slots");
    }

    // 4. Add slot with invalid times (end before start) → 400
    @Test @Order(4)
    void addSlotInvalidTimesReturns400() {
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        Instant end   = start.minus(1, ChronoUnit.HOURS);

        try {
            rest.exchange(
                    url("/api/v1/groups/" + groupId + "/availability"),
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(
                            "startsAt", start.toString(),
                            "endsAt",   end.toString()
                    ), authHeaders(accessToken)), Map.class);
            fail("Expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // 5. Delete slot → 204
    @Test @Order(5)
    void deleteSlotSucceeds() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability/" + slotId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(accessToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // 6. Delete non-existent slot → 404
    @Test @Order(6)
    void deleteNonExistentSlotReturns404() {
        try {
            rest.exchange(
                    url("/api/v1/groups/" + groupId + "/availability/00000000-0000-0000-0000-000000000000"),
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(accessToken)), Void.class);
            fail("Expected 404");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
