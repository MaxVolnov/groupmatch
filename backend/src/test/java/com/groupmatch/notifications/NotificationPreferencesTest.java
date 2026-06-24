package com.groupmatch.notifications;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NotificationPreferencesTest extends BaseIntegrationTest {

    private static final String EMAIL    = "notif-prefs@groupmatch-test.io";
    private static final String PASSWORD = "Prefs1!";

    String accessToken;

    @BeforeAll
    void setUp() {
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Prefs Tester"), jsonHeaders()), Map.class);

        ResponseEntity<Map> signinResp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                        jsonHeaders()), Map.class);
        accessToken = signinResp.getBody().get("accessToken").toString();
    }

    // ── 1. get preferences → 200, all 4 fields present ───────────────────────

    @Test @Order(1)
    void getPreferencesReturnsDefaults() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/me/notification-preferences"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKeys(
                "emailMemberJoined", "emailMeetingReminder",
                "inappMemberJoined", "inappMeetingCreated");
        assertThat((Boolean) resp.getBody().get("inappMemberJoined")).isTrue();
        assertThat((Boolean) resp.getBody().get("emailMeetingReminder")).isTrue();
    }

    // ── 2. patch preference → updated value reflected ─────────────────────────

    @Test @Order(2)
    void updatePreferenceSucceeds() {
        var body = Map.of("emailMeetingReminder", false);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/me/notification-preferences"), HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Boolean) resp.getBody().get("emailMeetingReminder")).isFalse();
        assertThat((Boolean) resp.getBody().get("emailMemberJoined")).isTrue();
    }
}
