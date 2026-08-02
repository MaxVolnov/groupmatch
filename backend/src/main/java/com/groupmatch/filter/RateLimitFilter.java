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
    private final ClientIpResolver clientIpResolver;

    private final Map<String, Bucket> signupBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> signinBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(int signupCapacity, int signinCapacity, int refreshCapacity,
                           ClientIpResolver clientIpResolver) {
        this.signupCapacity = signupCapacity;
        this.signinCapacity = signinCapacity;
        this.refreshCapacity = refreshCapacity;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, Bucket> bucketMap;
        int capacity;

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
