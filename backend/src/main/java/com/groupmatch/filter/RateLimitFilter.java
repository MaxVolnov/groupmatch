package com.groupmatch.filter;

import com.groupmatch.security.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final int signupCapacity;
    private final int signinCapacity;
    private final int refreshCapacity;
    private final int invitePreviewCapacity;
    private final ClientIpResolver clientIpResolver;

    private final Map<String, Bucket> signupBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> signinBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> invitePreviewBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(int signupCapacity, int signinCapacity, int refreshCapacity,
                           int invitePreviewCapacity, ClientIpResolver clientIpResolver) {
        this.signupCapacity = signupCapacity;
        this.signinCapacity = signinCapacity;
        this.refreshCapacity = refreshCapacity;
        this.invitePreviewCapacity = invitePreviewCapacity;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        Map<String, Bucket> bucketMap;
        int capacity;

        // GET-путь проверяем до общего отсечения по методу. Раньше фильтр
        // начинался со строки «не POST — пропустить», и любой публичный GET
        // оставался без лимита вовсе. Для превью приглашения это означало бы
        // свободный перебор пространства токенов: эндпоинт отвечает 200 и на
        // несуществующий токен, так что отличить попадание от промаха можно по
        // одному полю ответа.
        if (isInvitePreviewRequest(method, path)) {
            bucketMap = invitePreviewBuckets;
            capacity = invitePreviewCapacity;
            enforce(request, response, filterChain, bucketMap, capacity, path);
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("/api/v1/auth/signup".equals(path) || "/api/v1/auth/guest".equals(path)
                || "/api/v1/auth/forgot-password".equals(path)
                || "/api/v1/auth/resend-verification".equals(path)
                || "/api/v1/auth/upgrade-guest".equals(path)) {
            bucketMap = signupBuckets;
            capacity = signupCapacity;
        } else if ("/api/v1/auth/signin".equals(path)) {
            bucketMap = signinBuckets;
            capacity = signinCapacity;
        } else if ("/api/v1/auth/refresh".equals(path)) {
            bucketMap = refreshBuckets;
            capacity = refreshCapacity;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        enforce(request, response, filterChain, bucketMap, capacity, path);
    }

    /**
     * {@code GET /api/v1/invites/{token}} и {@code …/name-taken}.
     * {@code POST …/join} сюда не попадает — он идёт своим путём и требует
     * авторизации.
     */
    private static boolean isInvitePreviewRequest(String method, String path) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        String[] parts = path.split("/");
        if (parts.length < 5 || parts.length > 6) return false;
        if (!("api".equals(parts[1]) && "v1".equals(parts[2]) && "invites".equals(parts[3]))) return false;
        return parts.length == 5 || "name-taken".equals(parts[5]);
    }

    private void enforce(HttpServletRequest request, HttpServletResponse response,
                         FilterChain filterChain, Map<String, Bucket> bucketMap,
                         int capacity, String path) throws ServletException, IOException {
        String ip = clientIpResolver.resolve(request);
        log.debug("RateLimit check: ip={}, path={}", ip, path);

        Bucket bucket = bucketMap.computeIfAbsent(ip, k -> newBucket(capacity));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            log.debug("RateLimit exceeded: ip={}, path={}", ip, path);
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write(String.format(
                "{\"code\":\"too_many_requests\",\"message\":\"Too many requests. Please try again later.\",\"details\":null,\"timestamp\":\"%s\"}",
                Instant.now()
            ));
        }
    }

    private Bucket newBucket(int capacity) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofHours(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
