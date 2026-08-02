package com.groupmatch.security;

import com.groupmatch.exception.WebhookVerificationException;
import com.groupmatch.util.CidrMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Проверка подлинности вебхука ЮKassa.
 *
 * До этого {@code POST /api/v1/payments/yookassa/webhook} принимал любой запрос
 * без авторизации — то есть кто угодно мог активировать себе PRO, отправив
 * {@code {"event":"payment.succeeded", ...}}.
 *
 * Две независимые проверки, обе включаются конфигурацией:
 *
 * 1. IP-allowlist ({@code yookassa.webhook.allowed-ips}) — официальный механизм
 *    ЮKassa: уведомления приходят только с их опубликованных диапазонов.
 * 2. HMAC-SHA256 подпись тела ({@code yookassa.webhook.secret}) — для установок,
 *    где перед приложением стоит подписывающий прокси/релей.
 *
 * Fail-closed: если ЮKassa настроена по-боевому (shop-id != stub), а ни одна
 * проверка не включена — вебхук отклоняется. Иначе «включим потом» превращается
 * в дыру, которую никто не заметит.
 */
@Component
@Slf4j
public class YooKassaWebhookVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final String signatureHeader;
    private final List<CidrMatcher> allowedIps;
    private final boolean ipCheckEnabled;
    private final boolean stubMode;

    public YooKassaWebhookVerifier(
            @Value("${yookassa.shop-id:stub}") String shopId,
            @Value("${yookassa.webhook.secret:}") String secret,
            @Value("${yookassa.webhook.signature-header:X-Webhook-Signature}") String signatureHeader,
            @Value("${yookassa.webhook.ip-check-enabled:false}") boolean ipCheckEnabled,
            @Value("${yookassa.webhook.allowed-ips:}") String allowedIps) {
        this.secret = secret == null ? "" : secret.trim();
        this.signatureHeader = signatureHeader;
        this.ipCheckEnabled = ipCheckEnabled;
        this.allowedIps = CidrMatcher.parseList(allowedIps);
        this.stubMode = shopId == null || shopId.isBlank() || "stub".equalsIgnoreCase(shopId);

        if (this.ipCheckEnabled && this.allowedIps.isEmpty()) {
            throw new IllegalStateException(
                    "yookassa.webhook.ip-check-enabled=true, но yookassa.webhook.allowed-ips пуст");
        }
        log.info("YooKassa webhook verification: signature={}, ipAllowlist={}, stubMode={}",
                hasSecret() ? "on" : "off", this.ipCheckEnabled ? "on" : "off", this.stubMode);
        if (!this.stubMode && !hasSecret() && !this.ipCheckEnabled) {
            log.error("YooKassa настроена боевым shop-id, но проверка вебхука не включена — "
                    + "все уведомления будут отклоняться. Задайте YOOKASSA_WEBHOOK_SECRET "
                    + "и/или YOOKASSA_WEBHOOK_IP_CHECK=true");
        }
    }

    public String signatureHeaderName() {
        return signatureHeader;
    }

    /**
     * @param body      сырое тело запроса — подпись считается ровно по нему,
     *                  пересериализованный JSON дал бы другой HMAC
     * @param signature значение заголовка подписи (может быть {@code null})
     * @param clientIp  реальный IP отправителя (см. {@link ClientIpResolver})
     * @throws WebhookVerificationException если запрос не подтверждён
     */
    public void verify(String body, String signature, String clientIp) {
        if (ipCheckEnabled && !CidrMatcher.anyMatch(allowedIps, clientIp)) {
            reject("IP не входит в allowlist", clientIp);
        }

        if (hasSecret()) {
            if (signature == null || signature.isBlank()) {
                reject("отсутствует заголовок " + signatureHeader, clientIp);
            }
            if (!signatureMatches(body, signature)) {
                reject("подпись не совпадает", clientIp);
            }
            return;
        }

        if (ipCheckEnabled) {
            return; // allowlist уже пройден и является достаточной проверкой
        }

        if (stubMode) {
            log.debug("YooKassa в stub-режиме: проверка вебхука пропущена");
            return;
        }

        reject("проверка вебхука не настроена (нет ни секрета, ни allowlist)", clientIp);
    }

    private boolean hasSecret() {
        return !secret.isEmpty();
    }

    private boolean signatureMatches(String body, String signature) {
        byte[] expected = hmacSha256(body);
        byte[] provided = decodeHexOrNull(stripPrefix(signature));
        if (provided == null) return false;
        // Сравнение постоянного времени — не даём подбирать подпись по таймингам.
        return MessageDigest.isEqual(expected, provided);
    }

    /** Допускаем формат {@code sha256=<hex>} — так шлют многие подписывающие прокси. */
    private static String stripPrefix(String signature) {
        String s = signature.trim();
        int eq = s.indexOf('=');
        return eq > 0 && s.regionMatches(true, 0, "sha256=", 0, 7) ? s.substring(eq + 1) : s;
    }

    private byte[] hmacSha256(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute webhook HMAC", e);
        }
    }

    private static byte[] decodeHexOrNull(String hex) {
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void reject(String reason, String clientIp) {
        log.warn("YooKassa webhook отклонён: {}. clientIp={}", reason, clientIp);
        throw new WebhookVerificationException("Webhook verification failed");
    }
}
