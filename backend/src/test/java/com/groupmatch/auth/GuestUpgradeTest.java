package com.groupmatch.auth;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GuestUpgradeTest extends BaseIntegrationTest {

    private static final String UPGRADE_EMAIL = "guest-upgraded@groupmatch-test.io";

    String guestToken;
    String upgradedToken;

    // ── 1. guest signin ───────────────────────────────────────────────────────

    @Test @Order(1)
    void guestSigninSucceeds() {
        var body = Map.of("displayName", "Guest Upgrader");
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/guest"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        guestToken = resp.getBody().get("accessToken").toString();
        assertThat(guestToken).isNotBlank();
    }

    // ── 2. upgrade guest → full account ──────────────────────────────────────

    @Test @Order(2)
    void upgradeGuestSucceeds() {
        var body = Map.of(
                "email", UPGRADE_EMAIL,
                "password", "Upgraded1!",
                "displayName", "Upgraded Guest"
        );
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/upgrade-guest"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(guestToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        upgradedToken = resp.getBody().get("accessToken").toString();
        assertThat(upgradedToken).isNotBlank();

        // New token should work and reflect the upgraded email
        ResponseEntity<Map> meResp = rest.exchange(
                url("/api/v1/me"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(upgradedToken)), Map.class);
        assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResp.getBody().get("email")).isEqualTo(UPGRADE_EMAIL);
    }

    // ── 3. upgrade again → 400 ───────────────────────────────────────────────

    @Test @Order(3)
    void upgradeAlreadyUpgradedReturns400() {
        var body = Map.of(
                "email", "second-upgrade@groupmatch-test.io",
                "password", "Upgraded1!",
                "displayName", "Second Upgrade"
        );
        try {
            rest.exchange(url("/api/v1/auth/upgrade-guest"), HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders(upgradedToken)), Map.class);
            fail("Expected 400");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
