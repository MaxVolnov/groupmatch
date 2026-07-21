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

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getRequestURI());
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
