package com.groupmatch.security;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "blacklist:access:";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

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

        try {
            String jwt = authHeader.substring(7);

            if (!jwtUtils.validateToken(jwt)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                return;
            }

            // Проверяем blacklist (logout)
            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jwt))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
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
        } catch (Exception e) {
            // Невалидный токен — продолжаем без аутентификации
        }

        filterChain.doFilter(request, response);
    }
}
