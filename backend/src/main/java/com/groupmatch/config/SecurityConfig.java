package com.groupmatch.config;

import com.groupmatch.filter.RateLimitFilter;
import com.groupmatch.security.JwtAuthenticationFilter;
import com.groupmatch.security.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
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
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.rate-limit.signup:5}")
    private int rateLimitSignup;

    @Value("${app.rate-limit.signin:10}")
    private int rateLimitSignin;

    @Value("${app.rate-limit.refresh:20}")
    private int rateLimitRefresh;

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(rateLimitSignup, rateLimitSignin, rateLimitRefresh);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtAuthFilter,
                                                    DaoAuthenticationProvider authProvider) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/signup",
                    "/api/v1/auth/signin",
                    "/api/v1/auth/guest",
                    "/api/v1/auth/refresh",
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authProvider)
            .addFilterBefore(rateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsServiceImpl userDetailsService) {
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

    // Spring Security 7 StrictHttpFirewall rejects unknown Host headers by default.
    // The app runs behind Railway's proxy, so allow any hostname — JWT secures the API.
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHostnames(hostname -> true);
        return web -> web.httpFirewall(firewall);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
