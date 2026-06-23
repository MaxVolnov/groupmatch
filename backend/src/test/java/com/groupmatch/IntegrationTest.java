package com.groupmatch;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.url", () ->
                "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    final RestTemplate rest = new RestTemplate(
            new org.springframework.http.client.HttpComponentsClientHttpRequestFactory());

    // Shared state passed between ordered tests
    static String accessToken;
    static String groupId;
    static String guestAccessToken;
    static String adminAccessToken;
    static String integrationUserId;
    static String inviteToken;
    static String secondUserAccessToken;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(accessToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders adminAuthHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(adminAccessToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ── 1. signup ────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void signup() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = Map.of(
                "email", "integration@groupmatch-test.io",
                "password", "IntTest1!",
                "displayName", "Integration Tester"
        );
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("id");
        integrationUserId = resp.getBody().get("id").toString();
        assertThat(integrationUserId).isNotBlank();
    }

    // ── 2. signin ────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    void signin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = Map.of(
                "email", "integration@groupmatch-test.io",
                "password", "IntTest1!"
        );
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        accessToken = (String) resp.getBody().get("accessToken");
        assertThat(accessToken).isNotBlank();
    }

    // ── 3. create group ──────────────────────────────────────────────────────

    @Test
    @Order(3)
    void createGroup() {
        var body = Map.of(
                "title", "Integration Test Group",
                "tzId", "Europe/Moscow"
        );
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        groupId = resp.getBody().get("id").toString();
        assertThat(groupId).isNotBlank();
    }

    // ── 4. add availability slot ─────────────────────────────────────────────

    @Test
    @Order(4)
    void addAvailability() {
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        var body = Map.of(
                "startsAt", tomorrow.toString(),
                "endsAt", tomorrow.plus(1, ChronoUnit.HOURS).toString()
        );
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("id");
    }

    // ── 5. get heatmap ───────────────────────────────────────────────────────

    @Test
    @Order(5)
    void getHeatmap() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/availability/heatmap"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("slots");
    }

    // ── 6. submit feedback ───────────────────────────────────────────────────

    @Test
    @Order(6)
    void submitFeedback() {
        var body = Map.of(
                "category", "BUG",
                "message", "This is a test bug report from the integration test suite."
        );
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/feedback"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("id");
        assertThat(resp.getBody().get("category")).isEqualTo("BUG");
        assertThat(resp.getBody().get("message")).isEqualTo(
                "This is a test bug report from the integration test suite.");
    }

    // ── 7. guest signup ──────────────────────────────────────────────────────

    @Test
    @Order(7)
    void guestSignup() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = Map.of("displayName", "Guest Tester");
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/guest"), HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("accessToken");
        guestAccessToken = (String) resp.getBody().get("accessToken");
        assertThat(guestAccessToken).isNotBlank();
    }

    // ── 8. signin with wrong password → 401 ─────────────────────────────────

    @Test
    @Order(8)
    void signinWrongPassword() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = Map.of(
                "email", "integration@groupmatch-test.io",
                "password", "WrongPassword999!"
        );
        try {
            rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
            fail("Expected 401");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── 9. ban user via admin, then signin → 403 ─────────────────────────────

    @Test
    @Order(9)
    void bannedUserSigninForbidden() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. Register admin candidate
        var adminSignupBody = Map.of(
                "email", "admin@groupmatch-test.io",
                "password", "AdminTest1!",
                "displayName", "Admin Tester"
        );
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(adminSignupBody, headers), Map.class);

        // 2. Promote to ADMIN via SQL (AdminPromotionRunner not active in tests)
        jdbcTemplate.execute(
                "UPDATE app_user SET role = 'ADMIN' WHERE email = 'admin@groupmatch-test.io'");

        // 3. Signin as admin
        var adminSigninBody = Map.of(
                "email", "admin@groupmatch-test.io",
                "password", "AdminTest1!"
        );
        ResponseEntity<Map> adminSigninResp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(adminSigninBody, headers), Map.class);
        assertThat(adminSigninResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminAccessToken = (String) adminSigninResp.getBody().get("accessToken");
        assertThat(adminAccessToken).isNotBlank();

        // 4. Ban the integration user
        var banBody = Map.of("reason", "test ban");
        ResponseEntity<Void> banResp = rest.exchange(
                url("/api/v1/admin/users/" + integrationUserId + "/ban"),
                HttpMethod.PATCH,
                new HttpEntity<>(banBody, adminAuthHeaders()),
                Void.class);
        assertThat(banResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 5. Attempt signin as banned user → expect 403
        var bannedSigninBody = Map.of(
                "email", "integration@groupmatch-test.io",
                "password", "IntTest1!"
        );
        try {
            rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                    new HttpEntity<>(bannedSigninBody, headers), Map.class);
            fail("Expected 403");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── 10. create invite ────────────────────────────────────────────────────

    @Test
    @Order(10)
    void createInvite() {
        var body = Map.of("maxUses", 0);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("token");
        inviteToken = resp.getBody().get("token").toString();
        assertThat(inviteToken).isNotBlank();
    }

    // ── 11. second user joins via invite ─────────────────────────────────────

    @Test
    @Order(11)
    void secondUserJoinsViaInvite() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Signup second user
        var signupBody = Map.of(
                "email", "second@groupmatch-test.io",
                "password", "Second1!",
                "displayName", "Second User"
        );
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(signupBody, headers), Map.class);

        // Signin second user
        var signinBody = Map.of(
                "email", "second@groupmatch-test.io",
                "password", "Second1!"
        );
        ResponseEntity<Map> signinResp = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(signinBody, headers), Map.class);
        assertThat(signinResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        secondUserAccessToken = (String) signinResp.getBody().get("accessToken");
        assertThat(secondUserAccessToken).isNotBlank();

        // Join via invite token
        HttpHeaders secondHeaders = new HttpHeaders();
        secondHeaders.setBearerAuth(secondUserAccessToken);
        secondHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> joinResp = rest.exchange(
                url("/api/v1/invites/" + inviteToken + "/join"), HttpMethod.POST,
                new HttpEntity<>(secondHeaders), Map.class);

        assertThat(joinResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(joinResp.getBody()).containsKey("groupId");
    }

    // ── 12. join same invite again is idempotent ──────────────────────────────

    @Test
    @Order(12)
    void joinSameInviteAgainIsIdempotent() {
        HttpHeaders secondHeaders = new HttpHeaders();
        secondHeaders.setBearerAuth(secondUserAccessToken);
        secondHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/invites/" + inviteToken + "/join"), HttpMethod.POST,
                new HttpEntity<>(secondHeaders), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── 13. join with invalid token → 404 ────────────────────────────────────

    @Test
    @Order(13)
    void joinInvalidTokenReturns404() {
        try {
            rest.exchange(url("/api/v1/invites/invalid-token-xyz/join"), HttpMethod.POST,
                    new HttpEntity<>(authHeaders()), Map.class);
            fail("Expected 404");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── 14. list invites ──────────────────────────────────────────────────────

    @Test
    @Order(14)
    void listInvites() {
        ResponseEntity<List> resp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.GET,
                new HttpEntity<>(authHeaders()), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();
        assertThat(resp.getBody())
                .anyMatch(item -> inviteToken.equals(((Map<?, ?>) item).get("token")));
    }
}
