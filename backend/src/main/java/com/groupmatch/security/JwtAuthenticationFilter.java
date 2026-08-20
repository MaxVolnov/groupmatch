package com.groupmatch.security;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Фильтр JWT-аутентификации.
 *
 * Алгоритм:
 *   1. Извлекаем Bearer-токен из заголовка Authorization.
 *   2. Проверяем подпись и срок действия.
 *   3. Проверяем Redis-blacklist (токены после logout).
 *   4. Восстанавливаем UserPrincipal из claims (без обращения к БД).
 *   5. Устанавливаем аутентификацию в SecurityContext с правильными authority.
 *
 * Обращения к БД нет — всё необходимое (userId, email, role, plan) есть в JWT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "blacklist:access:";

    // Must match exactly the permitAll() paths in SecurityConfig — not more, not less.
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/signin",
            "/api/v1/auth/guest",
            "/api/v1/auth/refresh",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/payments/yookassa/webhook",
            "/actuator/health",
            "/actuator/info"
    );

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    /** Группы health-эндпоинта: /actuator/health/liveness, …/readiness. */
    private static final String HEALTH_GROUP_PREFIX = "/actuator/health/";

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PUBLIC_PATHS.contains(uri)
                || uri.startsWith(HEALTH_GROUP_PREFIX)
                || isGroupCalendarFeed(uri)
                || isInvitePreview(request.getMethod(), uri);
    }

    /**
     * {@code GET /api/v1/invites/{token}} и {@code …/name-taken} — публичное
     * превью приглашения. Открывается до входа, Authorization не приходит.
     *
     * Метод проверяем явно: по тому же префиксу лежит
     * {@code POST /api/v1/invites/{token}/join}, и он авторизации требует.
     */
    private static boolean isInvitePreview(String method, String uri) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        String[] parts = uri.split("/");
        // ["", "api", "v1", "invites", token] или [..., token, "name-taken"]
        if (parts.length < 5 || parts.length > 6) return false;
        if (!("api".equals(parts[1]) && "v1".equals(parts[2]) && "invites".equals(parts[3]))) return false;
        return parts.length == 5 || "name-taken".equals(parts[5]);
    }

    /**
     * {@code GET /api/v1/groups/{id}/calendar.ics} — .ics-фид для календарных
     * клиентов: Authorization они не шлют, доступ даёт токен в query.
     *
     * Ровно тот же путь, что и в {@code SecurityConfig}: проверяем количество
     * сегментов, чтобы не оказаться шире permitAll-матчера.
     */
    private static boolean isGroupCalendarFeed(String uri) {
        if (!uri.endsWith("/calendar.ics")) return false;
        String[] parts = uri.split("/");
        // ["", "api", "v1", "groups", "{id}", "calendar.ics"]
        return parts.length == 6
                && "api".equals(parts[1])
                && "v1".equals(parts[2])
                && "groups".equals(parts[3]);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean authenticated = false;

        try {
            String jwt = authHeader.substring(7);

            if (!jwtUtils.validateToken(jwt)) {
                writeUnauthorized(response);
                return;
            }

            // Проверяем blacklist (logout)
            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jwt))) {
                writeUnauthorized(response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UUID userId = jwtUtils.extractUserId(jwt);
                String email = jwtUtils.extractEmail(jwt);
                Role role    = jwtUtils.extractRole(jwt);
                Plan plan    = jwtUtils.extractPlan(jwt);

                UserPrincipal principal = new UserPrincipal(userId, email, role, plan);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()   // ROLE_USER / ROLE_ADMIN
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            authenticated = true;
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
        }

        if (!authenticated) {
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
    }
}
