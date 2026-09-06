package com.groupmatch.config;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кэширование preflight-ответа.
 *
 * Без {@code Access-Control-Max-Age} браузер спрашивает разрешение перед каждым
 * запросом: на странице профиля это сорок запросов вместо двадцати, и каждый
 * лишний — полный круг до Москвы.
 *
 * Проверяется настоящий ответ приложения на OPTIONS, а не значение поля в
 * конфигурации: заголовок ставит фильтр Spring Security, и «поле выставлено» и
 * «заголовок доехал» — это два разных утверждения.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CorsPreflightTest extends BaseIntegrationTest {

    /** Разрешённый origin из app.cors.allowed-origins в application-test.yml. */
    private static final String ORIGIN = "http://localhost:3000";

    private ResponseEntity<String> preflight(String origin, String method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ORIGIN, origin);
        headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
        headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
        return rest.exchange(url(path), HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }

    @Test
    void preflightResponseCarriesMaxAge() {
        ResponseEntity<String> response = preflight(ORIGIN, "GET", "/api/v1/me");

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("preflight должен проходить")
                .isTrue();

        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_MAX_AGE))
                .as("без этого заголовка браузер переспрашивает перед каждым запросом")
                .isEqualTo(String.valueOf(SecurityConfig.PREFLIGHT_CACHE_SECONDS));
    }

    /**
     * Два часа — фактический потолок у Chromium. Просить больше бессмысленно, а
     * платить за это пришлось бы временем, за которое до людей доедет смена
     * CORS-политики.
     */
    @Test
    void maxAgeIsTwoHours() {
        assertThat(SecurityConfig.PREFLIGHT_CACHE_SECONDS).isEqualTo(7200L);
    }

    /** Заголовок нужен на всех путях, а не только на одном. */
    @Test
    void maxAgeIsSetOnEveryPath() {
        for (String path : new String[]{
                "/api/v1/auth/signin", "/api/v1/groups", "/api/v1/notifications"}) {
            assertThat(preflight(ORIGIN, "POST", path)
                    .getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_MAX_AGE))
                    .as("preflight на %s", path)
                    .isEqualTo(String.valueOf(SecurityConfig.PREFLIGHT_CACHE_SECONDS));
        }
    }

    /**
     * Кэширование не должно расширять список разрешённых источников: чужой
     * origin по-прежнему не получает разрешения.
     */
    @Test
    void unknownOriginStillGetsNoPermission() {
        ResponseEntity<String> response;
        try {
            response = preflight("https://evil.invalid", "GET", "/api/v1/me");
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            assertThat(e.getStatusCode().value()).as("отказ на чужой origin").isEqualTo(403);
            return;
        }
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .as("чужой origin не должен получать разрешения")
                .isNull();
    }
}
