package com.groupmatch;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        registry.add("spring.redis.url", () ->
                "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    final RestTemplate rest = new RestTemplate();

    // Shared state passed between ordered tests
    static String accessToken;
    static String groupId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(accessToken);
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
}
