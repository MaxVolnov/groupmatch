package com.groupmatch.config;

import com.groupmatch.security.JwtAuthenticationFilter;
import com.groupmatch.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Конфигурация Spring Security.
 *
 * Ролевая модель:
 * ┌──────────────────────────────────────────────────────────┐
 * │  Системные роли (Role enum, хранится в БД и JWT)         │
 * │  USER  — стандартный доступ к API (создание групп, слоты)│
 * │  ADMIN — доступ к /api/v1/admin/** (модерация, репорты) │
 * ├──────────────────────────────────────────────────────────┤
 * │  Групповые роли (GroupRole enum, таблица grp_member)      │
 * │  OWNER  — владелец группы (редактирование, инвайты, ...)  │
 * │  MEMBER — участник (только свои слоты)                   │
 * │  * проверяются через @PreAuthorize в сервисах            │
 * └──────────────────────────────────────────────────────────┘
 *
 * JWT-стратегия:
 *   - Stateless sessions (без server-side state).
 *   - JwtAuthenticationFilter парсит токен и восстанавливает UserPrincipal
 *     (включая ROLE_USER / ROLE_ADMIN) без обращения к БД.
 *   - Logout реализован через Redis blacklist с TTL = оставшееся время токена.
 *   - Refresh token rotation: старый токен помечается used, выдаётся новая пара.
 *
 * @EnableMethodSecurity включает:
 *   - @PreAuthorize("hasRole('ADMIN')")  — для admin-эндпоинтов
 *   - @PreAuthorize("hasRole('USER')")   — для обычных пользователей
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // Активирует @PreAuthorize / @PostAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Публичные эндпоинты
                .requestMatchers(
                    "/api/v1/auth/signup",
                    "/api/v1/auth/signin",
                    "/api/v1/auth/refresh",
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()
                // Только ADMIN
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // Всё остальное — любой аутентифицированный пользователь.
                // Тонкая проверка (OWNER vs MEMBER) — через @PreAuthorize в сервисах.
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Используется AuthenticationManager при signin через UserDetailsService. */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // Spring Security 7: UserDetailsService is a required constructor argument
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id: memory=65536 (64MB), iterations=3, parallelism=4
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // dev: localhost:3000; prod — переопределить через env/profile
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
