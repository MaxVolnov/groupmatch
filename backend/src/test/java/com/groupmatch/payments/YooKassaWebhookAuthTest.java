package com.groupmatch.payments;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * P0: вебхук ЮKassa принимал любой POST без авторизации — то есть любой мог
 * выдать себе PRO, отправив {"event":"payment.succeeded"}.
 *
 * Отдельный контекст с боевым shop-id и заданным секретом подписи.
 */
@TestPropertySource(properties = {
        "yookassa.shop-id=test-shop",
        "yookassa.webhook.secret=" + YooKassaWebhookAuthTest.SECRET,
        "app.trusted-proxies="
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class YooKassaWebhookAuthTest extends BaseIntegrationTest {

    static final String SECRET = "webhook-test-secret";

    /** Неизвестное событие: сервис его залогирует и вернётся штатно. */
    private static final String BODY = "{\"event\":\"unknown\",\"object\":{}}";

    @Test
    void rejectsRequestWithoutSignature() {
        assertThat(post(BODY, null)).isEqualTo(401);
    }

    @Test
    void rejectsRequestWithWrongSignature() {
        assertThat(post(BODY, sign(BODY, "wrong-secret"))).isEqualTo(401);
    }

    @Test
    void rejectsGarbageSignature() {
        assertThat(post(BODY, "not-hex-at-all")).isEqualTo(401);
        assertThat(post(BODY, "")).isEqualTo(401);
    }

    /** Подпись считается по сырому телу — тот же секрет, но другое тело не подходит. */
    @Test
    void rejectsSignatureOfDifferentBody() {
        String forged = "{\"event\":\"payment.succeeded\",\"object\":{\"id\":\"evil\"}}";
        assertThat(post(forged, sign(BODY, SECRET))).isEqualTo(401);
    }

    @Test
    void acceptsValidSignature() {
        assertThat(post(BODY, sign(BODY, SECRET))).isEqualTo(200);
    }

    @Test
    void acceptsValidSignatureWithSha256Prefix() {
        assertThat(post(BODY, "sha256=" + sign(BODY, SECRET))).isEqualTo(200);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int post(String body, String signature) {
        HttpHeaders headers = jsonHeaders();
        if (signature != null) headers.add("X-Webhook-Signature", signature);
        try {
            return rest.exchange(url("/api/v1/payments/yookassa/webhook"), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class).getStatusCode().value();
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Unexpected error: " + e.getMessage());
        }
    }

    static String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
