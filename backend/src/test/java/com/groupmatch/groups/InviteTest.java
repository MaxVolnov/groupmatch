package com.groupmatch.groups;

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
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InviteTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL    = "invite-owner@groupmatch-test.io";
    private static final String OWNER_PASSWORD = "Owner1!";
    private static final String JOINER_EMAIL    = "invite-joiner@groupmatch-test.io";
    private static final String JOINER_PASSWORD = "Joiner1!";

    String ownerToken;
    String joinerToken;
    String groupId;
    String inviteToken;
    String inviteId;

    @BeforeAll
    void setUp() {
        // Owner signup + signin
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", OWNER_PASSWORD,
                        "displayName", "Invite Owner"), jsonHeaders()), Map.class);
        ResponseEntity<Map> ownerSignin = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", OWNER_PASSWORD),
                        jsonHeaders()), Map.class);
        ownerToken = ownerSignin.getBody().get("accessToken").toString();

        // Create group
        ResponseEntity<Map> groupResp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Invite Test Group", "tzId", "UTC"),
                        authHeaders(ownerToken)), Map.class);
        groupId = groupResp.getBody().get("id").toString();

        // Joiner signup + signin
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", JOINER_EMAIL, "password", JOINER_PASSWORD,
                        "displayName", "Invite Joiner"), jsonHeaders()), Map.class);
        ResponseEntity<Map> joinerSignin = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", JOINER_EMAIL, "password", JOINER_PASSWORD),
                        jsonHeaders()), Map.class);
        joinerToken = joinerSignin.getBody().get("accessToken").toString();
    }

    // ── 1. create invite → 201 ────────────────────────────────────────────────

    @Test @Order(1)
    void createInviteSucceeds() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                new HttpEntity<>(Map.of("maxUses", 0), authHeaders(ownerToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        inviteToken = resp.getBody().get("token").toString();
        inviteId    = resp.getBody().get("id").toString();
        assertThat(inviteToken).isNotBlank();
    }

    // ── 2. joiner joins via invite → 200 ─────────────────────────────────────

    @Test @Order(2)
    void secondUserJoinsViaInvite() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/invites/" + inviteToken + "/join"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(joinerToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("groupId").toString()).isEqualTo(groupId);
    }

    // ── 3. join same invite again is idempotent ────────────────────────────────

    @Test @Order(3)
    void joinSameInviteAgainIsIdempotent() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/invites/" + inviteToken + "/join"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(joinerToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── 4. invalid token → 404 ────────────────────────────────────────────────

    @Test @Order(4)
    void joinInvalidTokenReturns404() {
        try {
            rest.exchange(url("/api/v1/invites/invalid-token-xyz/join"), HttpMethod.POST,
                    new HttpEntity<>(authHeaders(ownerToken)), Map.class);
            fail("Expected 404");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── 5. list invites ───────────────────────────────────────────────────────

    @Test @Order(5)
    void listInvitesContainsCreated() {
        ResponseEntity<List> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();
        assertThat(resp.getBody())
                .anyMatch(item -> inviteToken.equals(((Map<?, ?>) item).get("token")));
    }

    // ── 6. revoke invite → 204 ───────────────────────────────────────────────

    @Test @Order(6)
    void revokeInviteSucceeds() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites/" + inviteId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ownerToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ── 7. join revoked invite → 4xx ─────────────────────────────────────────

    @Test @Order(7)
    void joinRevokedInviteIsRejected() {
        try {
            rest.exchange(url("/api/v1/invites/" + inviteToken + "/join"), HttpMethod.POST,
                    new HttpEntity<>(authHeaders(joinerToken)), Map.class);
            fail("Expected 4xx");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode().is4xxClientError()).isTrue();
        }
    }
}
