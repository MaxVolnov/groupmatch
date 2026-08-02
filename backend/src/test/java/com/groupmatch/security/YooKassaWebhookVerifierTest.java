package com.groupmatch.security;

import com.groupmatch.exception.WebhookVerificationException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class YooKassaWebhookVerifierTest {

    private static final String BODY = "{\"event\":\"payment.succeeded\"}";
    private static final String SECRET = "s3cret";
    private static final String YOOKASSA_IPS = "185.71.76.0/27,77.75.156.11,2a02:5180::/32";
    private static final String HEADER = "X-Webhook-Signature";

    private static YooKassaWebhookVerifier verifier(String shopId, String secret,
                                                    boolean ipCheck, String allowedIps) {
        return new YooKassaWebhookVerifier(shopId, secret, HEADER, ipCheck, allowedIps);
    }

    private static String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── Подпись ──────────────────────────────────────────────────────────────

    @Test
    void acceptsValidSignature() {
        var v = verifier("real-shop", SECRET, false, "");
        assertThatCode(() -> v.verify(BODY, sign(BODY, SECRET), "1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrWrongSignature() {
        var v = verifier("real-shop", SECRET, false, "");
        assertThatThrownBy(() -> v.verify(BODY, null, "1.2.3.4"))
                .isInstanceOf(WebhookVerificationException.class);
        assertThatThrownBy(() -> v.verify(BODY, "  ", "1.2.3.4"))
                .isInstanceOf(WebhookVerificationException.class);
        assertThatThrownBy(() -> v.verify(BODY, sign(BODY, "other"), "1.2.3.4"))
                .isInstanceOf(WebhookVerificationException.class);
        assertThatThrownBy(() -> v.verify(BODY, "zzzz", "1.2.3.4"))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void signatureIsBoundToExactBody() {
        var v = verifier("real-shop", SECRET, false, "");
        String signature = sign(BODY, SECRET);
        assertThatThrownBy(() -> v.verify(BODY + " ", signature, "1.2.3.4"))
                .isInstanceOf(WebhookVerificationException.class);
    }

    // ── IP allowlist ─────────────────────────────────────────────────────────

    @Test
    void allowlistAcceptsYookassaRangesOnly() {
        var v = verifier("real-shop", "", true, YOOKASSA_IPS);
        assertThatCode(() -> v.verify(BODY, null, "185.71.76.5")).doesNotThrowAnyException();
        assertThatCode(() -> v.verify(BODY, null, "77.75.156.11")).doesNotThrowAnyException();
        assertThatCode(() -> v.verify(BODY, null, "2a02:5180::9")).doesNotThrowAnyException();
        assertThatThrownBy(() -> v.verify(BODY, null, "203.0.113.7"))
                .isInstanceOf(WebhookVerificationException.class);
        assertThatThrownBy(() -> v.verify(BODY, null, "185.71.76.32"))
                .isInstanceOf(WebhookVerificationException.class);
    }

    /** Обе проверки включены — нужно пройти обе. */
    @Test
    void allowlistAndSignatureAreBothRequiredWhenBothEnabled() {
        var v = verifier("real-shop", SECRET, true, YOOKASSA_IPS);
        assertThatCode(() -> v.verify(BODY, sign(BODY, SECRET), "185.71.76.5")).doesNotThrowAnyException();
        assertThatThrownBy(() -> v.verify(BODY, sign(BODY, SECRET), "203.0.113.7"))
                .isInstanceOf(WebhookVerificationException.class);
        assertThatThrownBy(() -> v.verify(BODY, "bad", "185.71.76.5"))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void ipCheckWithoutAllowlistIsAConfigurationError() {
        assertThatThrownBy(() -> verifier("real-shop", "", true, ""))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Fail-closed / stub ───────────────────────────────────────────────────

    /** Боевой shop-id без единой настроенной проверки — отклоняем, а не пропускаем. */
    @Test
    void failsClosedWhenNothingIsConfiguredInProduction() {
        var v = verifier("real-shop", "", false, "");
        assertThatThrownBy(() -> v.verify(BODY, null, "1.2.3.4"))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void stubModeSkipsVerification() {
        assertThatCode(() -> verifier("stub", "", false, "").verify(BODY, null, "1.2.3.4"))
                .doesNotThrowAnyException();
        assertThatCode(() -> verifier("", "", false, "").verify(BODY, null, "1.2.3.4"))
                .doesNotThrowAnyException();
    }

    /** Даже в stub-режиме заданный секрет обязателен к проверке. */
    @Test
    void stubModeStillHonoursConfiguredSecret() {
        var v = verifier("stub", SECRET, false, "");
        assertThatThrownBy(() -> v.verify(BODY, null, "1.2.3.4"))
                .isInstanceOf(WebhookVerificationException.class);
        assertThatCode(() -> v.verify(BODY, sign(BODY, SECRET), "1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void exposesConfiguredHeaderName() {
        assertThat(verifier("stub", "", false, "").signatureHeaderName()).isEqualTo(HEADER);
    }
}
