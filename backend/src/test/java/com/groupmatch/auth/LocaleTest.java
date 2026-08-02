package com.groupmatch.auth;

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
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * A1: язык интерфейса не доезжал до бэкенда — письма всегда уходили на ru.
 * Контракт со стороны сервера: locale принимается при регистрации и
 * обновляется через PATCH /me (именно его теперь дёргает setLanguage).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocaleTest extends BaseIntegrationTest {

    private static final String EN_EMAIL = "locale-en@groupmatch-test.io";
    private static final String RU_EMAIL = "locale-default@groupmatch-test.io";
    private static final String PASSWORD = "LocaleTest1!";

    private String enToken;

    @BeforeAll
    void setUp() {
        cleanupUser(EN_EMAIL);
        cleanupUser(RU_EMAIL);
    }

    @AfterAll
    void tearDown() {
        cleanupUser(EN_EMAIL);
        cleanupUser(RU_EMAIL);
    }

    @Test @Order(1)
    void signupStoresRequestedLocale() {
        signup(EN_EMAIL, "Locale EN", "en");
        assertThat(localeInDb(EN_EMAIL)).isEqualTo("en");
        enToken = signin(EN_EMAIL);
    }

    @Test @Order(2)
    void signupWithoutLocaleFallsBackToRu() {
        signup(RU_EMAIL, "Locale Default", null);
        assertThat(localeInDb(RU_EMAIL)).isEqualTo("ru");
    }

    /** Ровно то, что делает setLanguage() на фронте для авторизованного пользователя. */
    @Test @Order(3)
    void patchMeUpdatesLocale() {
        var resp = rest.exchange(url("/api/v1/me"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("locale", "ru"), authHeaders(enToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("locale")).isEqualTo("ru");
        assertThat(localeInDb(EN_EMAIL)).isEqualTo("ru");

        rest.exchange(url("/api/v1/me"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("locale", "en"), authHeaders(enToken)), Map.class);
        assertThat(localeInDb(EN_EMAIL)).isEqualTo("en");
    }

    @Test @Order(4)
    void unsupportedLocaleIsRejected() {
        try {
            rest.exchange(url("/api/v1/me"), HttpMethod.PATCH,
                    new HttpEntity<>(Map.of("locale", "de"), authHeaders(enToken)), Map.class);
            fail("Expected 400");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(localeInDb(EN_EMAIL)).isEqualTo("en");
    }

    /** Другие поля профиля не должны сбрасывать locale. */
    @Test @Order(5)
    void updatingDisplayNameKeepsLocale() {
        rest.exchange(url("/api/v1/me"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("displayName", "Renamed"), authHeaders(enToken)), Map.class);
        assertThat(localeInDb(EN_EMAIL)).isEqualTo("en");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void signup(String email, String displayName, String locale) {
        var body = locale == null
                ? Map.of("email", email, "password", PASSWORD, "displayName", displayName)
                : Map.of("email", email, "password", PASSWORD, "displayName", displayName,
                         "locale", locale);
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);
    }

    private String signin(String email) {
        return rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", email, "password", PASSWORD), jsonHeaders()),
                        Map.class)
                .getBody().get("accessToken").toString();
    }

    private String localeInDb(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT locale FROM app_user WHERE email = ?", String.class, email);
    }
}
