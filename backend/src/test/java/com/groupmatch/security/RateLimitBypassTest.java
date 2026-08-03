package com.groupmatch.security;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * P0: подделанный X-Forwarded-For не должен сбрасывать счётчик rate-limit.
 *
 * Собственный контекст (@TestPropertySource) — чтобы у фильтра были свои
 * бакеты и низкий лимит, не задевающие остальные тесты сьюта.
 * {@code app.trusted-proxies} пуст: тестовый клиент ходит напрямую, без прокси.
 */
@TestPropertySource(properties = {
        "app.rate-limit.signin=3",
        "app.trusted-proxies="
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RateLimitBypassTest extends BaseIntegrationTest {

    private static final Map<String, String> BAD_CREDENTIALS =
            Map.of("email", "nobody@groupmatch-test.io", "password", "WrongPass1!");

    /**
     * Отдельный клиент без ретраев: HttpClient5 из BaseIntegrationTest сам
     * повторяет 429 и честно спит по Retry-After — здесь это полчаса ожидания.
     *
     * Именно HttpComponents, а не SimpleClientHttpRequestFactory: последний
     * ходит через HttpURLConnection, а тот молча выбрасывает Origin (он в
     * списке restricted headers JDK) — CORS-проверка ниже никогда бы не
     * сработала по-настоящему.
     */
    private final RestTemplate noRetry = new RestTemplate(
            new HttpComponentsClientHttpRequestFactory(
                    HttpClients.custom().disableAutomaticRetries().build()));

    @Test @Order(1)
    void limitIsEnforcedPerRealClient() {
        for (int i = 1; i <= 3; i++) {
            assertThat(signin(null)).as("попытка #%d до лимита", i).isEqualTo(401);
        }
        assertThat(signin(null)).as("4-я попытка — лимит исчерпан").isEqualTo(429);
    }

    @Test @Order(2)
    void forgedForwardedForDoesNotResetTheLimit() {
        assertThat(signin("1.2.3.4")).as("подделанный XFF").isEqualTo(429);
        assertThat(signin("9.9.9.9, 8.8.8.8")).as("подделанная цепочка XFF").isEqualTo(429);
        assertThat(signin("127.0.0.1")).as("XFF, притворяющийся прокси").isEqualTo(429);
        assertThat(signin("not-an-ip")).as("мусор в XFF").isEqualTo(429);
    }

    /**
     * P1: 429 нёс Retry-After, но браузер прятал его от JS — в CORS не был
     * задан exposedHeaders, и обратный отсчёт в ErrorMessage.tsx не работал.
     *
     * Проверяем оба заголовка на одном ответе: сам Retry-After и разрешение
     * его прочитать. Лимит к этому моменту уже исчерпан предыдущими тестами.
     */
    @Test @Order(3)
    void rateLimitResponseExposesRetryAfterToBrowser() {
        HttpHeaders headers = jsonHeaders();
        headers.add(HttpHeaders.ORIGIN, "http://localhost:3000"); // из app.cors.allowed-origins

        HttpHeaders responseHeaders;
        try {
            noRetry.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                    new HttpEntity<>(BAD_CREDENTIALS, headers), Map.class);
            fail("Ожидали 429 — лимит должен быть исчерпан предыдущими тестами");
            return;
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(429);
            responseHeaders = e.getResponseHeaders();
        }

        assertThat(responseHeaders).isNotNull();
        String retryAfter = responseHeaders.getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).as("сам Retry-After").isNotNull();
        assertThat(Integer.parseInt(retryAfter)).as("секунды до сброса лимита").isPositive();

        String exposed = responseHeaders.getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        assertThat(exposed)
                .as("без этого заголовка браузер не отдаст Retry-After в JS")
                .isNotNull()
                .containsIgnoringCase("Retry-After");
    }

    private int signin(String forwardedFor) {
        HttpHeaders headers = jsonHeaders();
        if (forwardedFor != null) headers.add("X-Forwarded-For", forwardedFor);
        try {
            return noRetry.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                    new HttpEntity<>(BAD_CREDENTIALS, headers), Map.class)
                    .getStatusCode().value();
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Unexpected error: " + e.getMessage());
        }
    }
}
