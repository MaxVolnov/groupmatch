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
import com.groupmatch.util.TokenMasker;

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

    /**
     * Пользователь, чьи access-токены больше не действуют. Ключ по человеку, а
     * не по токену: конкретные выданные токены серверу неизвестны.
     */
    private static final String DISABLED_USER_PREFIX  = "disabled:user:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${jwt.expiration.refresh}")
    private long refreshExpirationMs;

    @Value("${jwt.expiration.refresh-guest}")
    private long refreshGuestExpirationMs;

    @Value("${jwt.expiration.access}")
    private long accessExpirationMs;

    // ─── Создание ─────────────────────────────────────────────────────────────

    /** Генерирует и сохраняет новый refresh-токен для пользователя. */
    public String issue(UUID userId) {
        return issueWithTtl(userId, Duration.ofMillis(refreshExpirationMs));
    }

    /** Генерирует refresh-токен с увеличенным TTL для гостевых аккаунтов (90 дней). */
    public String issueForGuest(UUID userId) {
        return issueWithTtl(userId, Duration.ofMillis(refreshGuestExpirationMs));
    }

    /**
     * Общее тело обеих выдач: до этого два метода отличались ровно одной
     * строкой — источником TTL, — а остальные пять совпадали дословно.
     *
     * Схема ключей при этом не меняется: те же три операции в том же порядке.
     */
    private String issueWithTtl(UUID userId, Duration ttl) {
        String token = generateToken();

        redis.opsForValue().set(REFRESH_PREFIX + token, userId.toString(), ttl);
        redis.opsForSet().add(REFRESH_USER_PREFIX + userId, token);
        redis.expire(REFRESH_USER_PREFIX + userId, ttl);

        log.debug("Issued refresh token. userId={}, ttl={}ms", userId, ttl.toMillis());
        return token;
    }

    // ─── Валидация и ротация ──────────────────────────────────────────────────

    /**
     * Проверяет токен: если валиден — удаляет его (rotation) и возвращает userId.
     * Если не найден — возвращает null (вызывающий должен вернуть 401).
     */
    public UUID validateAndRotate(String token) {
        log.debug("Refresh attempt. token={}", TokenMasker.mask(token));
        String userIdStr = redis.opsForValue().getAndDelete(REFRESH_PREFIX + token);

        if (userIdStr == null) {
            log.warn("Refresh token not found in Redis (expired or already rotated). token={}", TokenMasker.mask(token));
            return null;
        }

        UUID userId = UUID.fromString(userIdStr);
        // Удаляем из user-set (токен уже использован)
        redis.opsForSet().remove(REFRESH_USER_PREFIX + userId, token);
        log.debug("Refresh token valid. userId={}, issuing new token", userId);
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

    /** Инвалидирует ВСЕ refresh-токены пользователя (псевдоним для смены пароля и аналогичных операций). */
    public void invalidateAllForUser(UUID userId) {
        invalidateAllSessions(userId);
    }

    /**
     * Закрывает доступ по уже выданным access-токенам.
     *
     * {@link #invalidateAllSessions} этого не делает и делать не может: она
     * убирает refresh-токены из Redis, а access-токен — самодостаточный JWT,
     * который фильтр проверяет подписью, не обращаясь ни к базе, ни к Redis за
     * состоянием пользователя. После удаления аккаунта такой токен продолжал
     * открывать API ещё до пятнадцати минут — проверено тестом, он получал 200
     * на {@code GET /api/v1/me} уже после DELETE.
     *
     * Метка ставится на пользователя, а не на токен: конкретных выданных
     * токенов мы не знаем. TTL равен времени жизни access-токена — дольше
     * держать нечего, к этому моменту все они истекут сами.
     */
    public void blockAccessTokens(UUID userId) {
        redis.opsForValue().set(
                DISABLED_USER_PREFIX + userId, "1", Duration.ofMillis(accessExpirationMs));
        log.info("Access tokens blocked for userId={}, ttl={}ms", userId, accessExpirationMs);
    }

    /** Снимает метку — при восстановлении аккаунта. Без этого не зайдёт и новый токен. */
    public void unblockAccessTokens(UUID userId) {
        redis.delete(DISABLED_USER_PREFIX + userId);
        log.info("Access tokens unblocked for userId={}", userId);
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

}
