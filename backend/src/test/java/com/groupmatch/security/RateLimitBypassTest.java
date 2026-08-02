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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
     * повторяет 429 и честно спит по Retry-After — здесь это час ожидания.
     */
    private final RestTemplate noRetry = new RestTemplate(new SimpleClientHttpRequestFactory());

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
