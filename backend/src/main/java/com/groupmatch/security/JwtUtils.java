package com.groupmatch.security;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration.access}")
    private long accessTokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ─── Генерация ────────────────────────────────────────────────────────────

    public String generateAccessToken(UUID userId, String email, Role role, Plan plan) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())   // "USER" | "ADMIN"
                .claim("plan", plan.name())   // "FREE" | "PRO" | "TEAM"
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ─── Извлечение claims ────────────────────────────────────────────────────

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaim(token, Claims::getSubject));
    }

    public String extractEmail(String token) {
        return extractClaim(token, c -> c.get("email", String.class));
    }

    public Role extractRole(String token) {
        String name = extractClaim(token, c -> c.get("role", String.class));
        return name != null ? Role.valueOf(name) : Role.USER;
    }

    public Plan extractPlan(String token) {
        String name = extractClaim(token, c -> c.get("plan", String.class));
        return name != null ? Plan.valueOf(name) : Plan.FREE;
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /** Возвращает оставшееся время жизни токена в миллисекундах (для blacklist TTL). */
    public long remainingTtlMillis(String token) {
        long exp = extractExpiration(token).getTime();
        long now = System.currentTimeMillis();
        return Math.max(0, exp - now);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    // ─── Валидация ────────────────────────────────────────────────────────────

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /** Проверяет подпись и срок действия. Blacklist проверяется в фильтре. */
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Внутреннее ───────────────────────────────────────────────────────────

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
