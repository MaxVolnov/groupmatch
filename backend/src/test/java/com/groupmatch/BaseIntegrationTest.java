package com.groupmatch;

import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    // When SPRING_DATASOURCE_URL is set (GitHub Actions services), skip Docker entirely.
    private static final boolean USE_EXTERNAL =
            System.getenv("SPRING_DATASOURCE_URL") != null;

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            USE_EXTERNAL ? null : new PostgreSQLContainer<>("postgres:16-alpine");

    @SuppressWarnings("resource")
    static final GenericContainer<?> redis =
            USE_EXTERNAL ? null : new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    static {
        if (!USE_EXTERNAL) {
            postgres.start();
            redis.start();
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        if (USE_EXTERNAL) {
            r.add("spring.datasource.url",      () -> System.getenv("SPRING_DATASOURCE_URL"));
            r.add("spring.datasource.username", () -> System.getenv("SPRING_DATASOURCE_USERNAME"));
            r.add("spring.datasource.password", () -> System.getenv("SPRING_DATASOURCE_PASSWORD"));
            r.add("spring.data.redis.url",      () -> System.getenv("SPRING_REDIS_URL"));
        } else {
            r.add("spring.datasource.url",      postgres::getJdbcUrl);
            r.add("spring.datasource.username", postgres::getUsername);
            r.add("spring.datasource.password", postgres::getPassword);
            r.add("spring.data.redis.url", () ->
                    "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        }
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected final RestTemplate rest = new RestTemplate(
            new org.springframework.http.client.HttpComponentsClientHttpRequestFactory());

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    protected void cleanupUser(String email) {
        jdbcTemplate.update("DELETE FROM app_user WHERE email = ?", email);
    }
}
