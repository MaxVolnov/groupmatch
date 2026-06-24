package com.groupmatch.admin;

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
public class AdminTest extends BaseIntegrationTest {

    private static final String USER_EMAIL    = "at-target@groupmatch-test.io";
    private static final String USER_PASSWORD = "Target1!";
    private static final String ADMIN_EMAIL    = "at-actor@groupmatch-test.io";
    private static final String ADMIN_PASSWORD = "Actor1!";

    String userToken;
    String userId;
    String adminToken;
    String adminId;

    @BeforeAll
    void setUp() {
        cleanupUser(USER_EMAIL);
        cleanupUser(ADMIN_EMAIL);

        // Regular user
        ResponseEntity<Map> signupResp = rest.exchange(
                url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", USER_EMAIL,
                        "password", USER_PASSWORD,
                        "displayName", "AT Target"), jsonHeaders()), Map.class);
        userId = signupResp.getBody().get("id").toString();

        ResponseEntity<Map> signinResp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", USER_EMAIL, "password", USER_PASSWORD),
                        jsonHeaders()), Map.class);
        userToken = signinResp.getBody().get("accessToken").toString();

        // Create a group and submit feedback as the regular user
        rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Admin Test Group", "tzId", "UTC"),
                        authHeaders(userToken)), Map.class);

        rest.exchange(url("/api/v1/feedback"), HttpMethod.POST,
                new HttpEntity<>(Map.of("category", "BUG", "message", "Admin test feedback"),
                        authHeaders(userToken)), Map.class);

        // Admin user
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", ADMIN_EMAIL,
                        "password", ADMIN_PASSWORD,
                        "displayName", "AT Admin"), jsonHeaders()), Map.class);

        jdbcTemplate.execute(
                "UPDATE app_user SET role = 'ADMIN' WHERE email = '" + ADMIN_EMAIL + "'");

        ResponseEntity<Map> adminSigninResp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                        jsonHeaders()), Map.class);
        adminToken = adminSigninResp.getBody().get("accessToken").toString();

        // Resolve adminId for self-role-change test
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> searchResp = rest.exchange(
                url("/api/v1/admin/users?search=at-actor"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> admins = (List<Map<?, ?>>) searchResp.getBody().get("users");
        adminId = admins.get(0).get("id").toString();
    }

    // ── 1. banned user cannot signin ─────────────────────────────────────────

    @Test @Order(1)
    void bannedUserSigninForbidden() {
        rest.exchange(url("/api/v1/admin/users/" + userId + "/ban"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("reason", "test ban"), authHeaders(adminToken)),
                Void.class);

        try {
            rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("email", USER_EMAIL, "password", USER_PASSWORD),
                            jsonHeaders()), Map.class);
            fail("Expected 403");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── 2. list all users ─────────────────────────────────────────────────────

    @Test @Order(2)
    @SuppressWarnings("unchecked")
    void adminListsUsers() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/admin/users"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> users = (List<Map<?, ?>>) resp.getBody().get("users");
        assertThat(users).isNotEmpty();
        assertThat(((Number) resp.getBody().get("totalElements")).longValue()).isGreaterThan(0);
    }

    // ── 3. search users ───────────────────────────────────────────────────────

    @Test @Order(3)
    @SuppressWarnings("unchecked")
    void adminSearchesUsers() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/admin/users?search=at-target"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> users = (List<Map<?, ?>>) resp.getBody().get("users");
        assertThat(users).isNotEmpty();
        assertThat(users.get(0).get("email").toString()).contains("at-target");
    }

    // ── 4. change user plan ───────────────────────────────────────────────────

    @Test @Order(4)
    @SuppressWarnings("unchecked")
    void adminChangesUserPlan() {
        ResponseEntity<Void> patchResp = rest.exchange(
                url("/api/v1/admin/users/" + userId + "/plan"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("plan", "PRO"), authHeaders(adminToken)), Void.class);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> verifyResp = rest.exchange(
                url("/api/v1/admin/users?search=at-target"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);
        List<Map<?, ?>> users = (List<Map<?, ?>>) verifyResp.getBody().get("users");
        assertThat(users.get(0).get("plan")).isEqualTo("PRO");
    }

    // ── 5. promote user to ADMIN ──────────────────────────────────────────────

    @Test @Order(5)
    @SuppressWarnings("unchecked")
    void adminPromotesUserToAdmin() {
        ResponseEntity<Void> patchResp = rest.exchange(
                url("/api/v1/admin/users/" + userId + "/role"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("role", "ADMIN"), authHeaders(adminToken)), Void.class);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> verifyResp = rest.exchange(
                url("/api/v1/admin/users?search=at-target"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);
        List<Map<?, ?>> users = (List<Map<?, ?>>) verifyResp.getBody().get("users");
        assertThat(users.get(0).get("role")).isEqualTo("ADMIN");
    }

    // ── 6. revoke ADMIN role ──────────────────────────────────────────────────

    @Test @Order(6)
    @SuppressWarnings("unchecked")
    void adminRevokesAdminRole() {
        ResponseEntity<Void> patchResp = rest.exchange(
                url("/api/v1/admin/users/" + userId + "/role"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("role", "USER"), authHeaders(adminToken)), Void.class);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> verifyResp = rest.exchange(
                url("/api/v1/admin/users?search=at-target"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);
        List<Map<?, ?>> users = (List<Map<?, ?>>) verifyResp.getBody().get("users");
        assertThat(users.get(0).get("role")).isEqualTo("USER");
    }

    // ── 7. admin cannot change own role ──────────────────────────────────────

    @Test @Order(7)
    void adminCannotChangeOwnRole() {
        try {
            rest.exchange(url("/api/v1/admin/users/" + adminId + "/role"), HttpMethod.PATCH,
                    new HttpEntity<>(Map.of("role", "USER"), authHeaders(adminToken)), Void.class);
            fail("Expected 403");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── 8. list groups ────────────────────────────────────────────────────────

    @Test @Order(8)
    @SuppressWarnings("unchecked")
    void adminListsGroups() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/admin/groups"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> groups = (List<Map<?, ?>>) resp.getBody().get("groups");
        assertThat(groups).isNotEmpty();
    }

    // ── 9. resolve feedback ───────────────────────────────────────────────────

    @Test @Order(9)
    @SuppressWarnings("unchecked")
    void adminResolvesFeedback() {
        ResponseEntity<Map> listResp = rest.exchange(
                url("/api/v1/admin/feedback"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> items = (List<Map<?, ?>>) listResp.getBody().get("items");
        assertThat(items).isNotEmpty();
        String feedbackId = items.get(0).get("id").toString();

        ResponseEntity<Void> resolveResp = rest.exchange(
                url("/api/v1/admin/feedback/" + feedbackId + "/resolve"), HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(adminToken)), Void.class);
        assertThat(resolveResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> resolvedResp = rest.exchange(
                url("/api/v1/admin/feedback?resolved=true"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), Map.class);
        List<Map<?, ?>> resolvedItems = (List<Map<?, ?>>) resolvedResp.getBody().get("items");
        assertThat(resolvedItems).anyMatch(item -> feedbackId.equals(item.get("id").toString()));
    }
}
