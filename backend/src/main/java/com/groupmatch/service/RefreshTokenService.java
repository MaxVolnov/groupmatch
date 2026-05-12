package com.groupmatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Управление refresh-токенами через Redis.
 *
 * Схема ключей:
 *   refresh:{token}          → userId (TTL = refresh expiry)
 *   refresh:user:{userId}    → Set<token> (для logout всех сессий)
 *   blacklist:access:{jwt}   → "1"        (TTL = оставшееся время access token)
 *
 * Refresh token rotation:
 *   1. При каждом /refresh: старый токен удаляется, выдаётся новый.
 *   2. Попытка reuse удалённого токена → не найден → 401.
 *   3. /logout инвалидирует access token (blacklist) и refresh token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final String REFRESH_PREFIX       = "refresh:";
    private static final String REFRESH_USER_PREFIX  = "refresh:user:";
    private static final String BLACKLIST_PREFIX      = "blacklist:access:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${jwt.expiration.refresh}")
    private long refreshExpirationMs;

    // ─── Создание ─────────────────────────────────────────────────────────────

    /** Генерирует и сохраняет новый refresh-токен для пользователя. */
    public String issue(UUID userId) {
        String token = generateToken();
        Duration ttl = Duration.ofMillis(refreshExpirationMs);

        redis.opsForValue().set(REFRESH_PREFIX + token, userId.toString(), ttl);
        redis.opsForSet().add(REFRESH_USER_PREFIX + userId, token);
        redis.expire(REFRESH_USER_PREFIX + userId, ttl);

        return token;
    }

    // ─── Валидация и ротация ──────────────────────────────────────────────────

    /**
     * Проверяет токен: если валиден — удаляет его (rotation) и возвращает userId.
     * Если не найден — возвращает null (вызывающий должен вернуть 401).
     */
    public UUID validateAndRotate(String token) {
        String userIdStr = redis.opsForValue().getAndDelete(REFRESH_PREFIX + token);

        if (userIdStr == null) {
            log.warn("Refresh token not found or already used: {}", maskToken(token));
            return null;
        }

        UUID userId = UUID.fromString(userIdStr);
        // Удаляем из user-set (токен уже использован)
        redis.opsForSet().remove(REFRESH_USER_PREFIX + userId, token);
        return userId;
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    /** Инвалидирует refresh-токен (добавляет access token в blacklist). */
    public void invalidateRefresh(String refreshToken) {
        String userIdStr = redis.opsForValue().getAndDelete(REFRESH_PREFIX + refreshToken);
        if (userIdStr != null) {
            redis.opsForSet().remove(REFRESH_USER_PREFIX + userIdStr, refreshToken);
        }
    }

    /** Добавляет access token в blacklist с TTL = оставшееся время жизни. */
    public void blacklistAccessToken(String accessToken, long remainingTtlMillis) {
        if (remainingTtlMillis > 0) {
            redis.opsForValue().set(
                    BLACKLIST_PREFIX + accessToken,
                    "1",
                    Duration.ofMillis(remainingTtlMillis)
            );
        }
    }

    /** Инвалидирует ВСЕ refresh-токены пользователя (принудительный logout всех сессий). */
    public void invalidateAllSessions(UUID userId) {
        var members = redis.opsForSet().members(REFRESH_USER_PREFIX + userId);
        if (members != null) {
            members.forEach(token -> redis.delete(REFRESH_PREFIX + token));
        }
        redis.delete(REFRESH_USER_PREFIX + userId);
        log.info("All sessions invalidated for userId={}", userId);
    }

    // ─── Внутреннее ───────────────────────────────────────────────────────────

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 8) + "***";
    }
}
