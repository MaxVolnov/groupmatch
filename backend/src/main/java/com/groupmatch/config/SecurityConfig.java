package com.groupmatch.config;

import com.groupmatch.filter.RateLimitFilter;
import com.groupmatch.security.ClientIpResolver;
import com.groupmatch.security.JwtAuthenticationFilter;
import com.groupmatch.security.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

import jakarta.servlet.http.HttpServletResponse;
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

    @Value("${app.rate-limit.invite-preview:2000}")
    private int rateLimitInvitePreview;

    @Bean
    public RateLimitFilter rateLimitFilter(ClientIpResolver clientIpResolver) {
        return new RateLimitFilter(rateLimitSignup, rateLimitSignin, rateLimitRefresh,
                rateLimitInvitePreview, clientIpResolver);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtAuthFilter,
                                                    DaoAuthenticationProvider authProvider,
                                                    RateLimitFilter rateLimitFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                        "{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/signup",
                    "/api/v1/auth/signin",
                    "/api/v1/auth/guest",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/verify-email",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/api/v1/payments/yookassa/webhook",
                    "/actuator/health"
                ).permitAll()
                // Группы health-эндпоинта: /actuator/health/liveness и
                // /actuator/health/readiness. Именно liveness должна опрашивать
                // платформа — см. комментарий в application.yml. Без этой
                // строки совпадает только точный путь /actuator/health, а
                // подпути отдают 401, и проверка платформы валится.
                .requestMatchers("/actuator/health/**").permitAll()
                // Всё остальное в actuator — только ADMIN.
                //
                // Порядок строк здесь значим: Spring Security берёт первое
                // совпадение, поэтому health и его группы выше остаются
                // публичными, а этот матчер ловит только то, что ниже них.
                //
                // ПОЧЕМУ ПО РОЛИ, А НЕ ПО АУТЕНТИФИКАЦИИ. Раньше служебные
                // пути закрывал общий `.anyRequest().authenticated()`, то есть
                // требовался просто валидный токен. Токен у нас выдаёт
                // публичный POST /api/v1/auth/guest — без почты и пароля, за
                // один анонимный запрос, и выданный им пользователь получает
                // Role.USER (AuthService.guestSignin). Значит «закрыто
                // аутентификацией» на практике означало «открыто всем».
                //
                // Список экспонируемых эндпоинтов в application.yml сокращён до
                // одного health, но полагаться только на него нельзя: он
                // решает, что существует, а не кому доступно, и правится одной
                // строкой. Эта строка — второй, независимый слой.
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // .ics-фид группы: календарные клиенты не шлют Authorization,
                // доступ авторизует токен в query (см. GroupCalendarService).
                // Дублируется в JwtAuthenticationFilter.shouldNotFilter —
                // списки обязаны совпадать.
                .requestMatchers(HttpMethod.GET, "/api/v1/groups/*/calendar.ics").permitAll()
                // Превью приглашения: экран /join/{token} открывается до входа,
                // и человек должен увидеть, кто и куда его зовёт, прежде чем
                // заводить аккаунт. Отдаёт только название группы и имя
                // пригласившего; перебор токенов ограничен RateLimitFilter —
                // публичный путь без лимита превратился бы в перебор.
                // Дублируется в JwtAuthenticationFilter.shouldNotFilter.
                .requestMatchers(HttpMethod.GET, "/api/v1/invites/*", "/api/v1/invites/*/name-taken").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authProvider)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
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
    // The app runs behind a platform proxy, so allow any hostname — JWT secures the API.
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
        // Браузер прячет от JS все заголовки ответа, кроме CORS-safelisted, пока
        // они не перечислены здесь явно. Без Retry-After фронтенд видел 429, но
        // не мог сказать, сколько ждать: обратный отсчёт в ErrorMessage.tsx был
        // написан и мёртв. Звёздочка тут не работает — с allowCredentials=true
        // wildcard в Access-Control-Expose-Headers браузером игнорируется.
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
