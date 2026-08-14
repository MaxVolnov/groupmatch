package com.groupmatch.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timeweb App Platform убивала контейнер через 105 секунд после успешного
 * старта: health-check платформы бил в /actuator/health, а тот отдавал 503,
 * потому что был недоступен Redis. Приложение при этом работало — Flyway
 * отработал, Tomcat обслуживал запросы.
 *
 * Лечение: платформа опрашивает /actuator/health/liveness (жив процесс), а не
 * агрегат по всем зависимостям. Здесь фиксируем предпосылки, без которых это
 * снова развалится, — и все они правятся одной строкой в конфиге, поэтому
 * ломаются незаметно.
 *
 * Проверяем конфиги и исходники, а не поднятый контекст: реальные запросы к
 * эндпоинту живут в интеграционном наборе, которому нужен Docker.
 */
public class ActuatorHealthConfigTest {

    private static final Path SRC = Path.of("src/main/java/com/groupmatch");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String resource) {
        try (InputStream in = ActuatorHealthConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("нет файла %s", resource).isNotNull();
            return (Map<String, Object>) new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось прочитать " + resource, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object at(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(key);
        }
        return current;
    }

    private static String source(String relative) {
        try {
            return Files.readString(SRC.resolve(relative));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось прочитать " + relative, e);
        }
    }

    /**
     * Без probes.enabled группы поднимаются только там, где Spring узнаёт
     * Kubernetes или Cloud Foundry. Timeweb он не узнаёт — и liveness отдаст
     * 404, что для health-check платформы такой же провал, как 503.
     */
    @Test
    void livenessProbeIsEnabledExplicitly() {
        assertThat(at(yaml("application.yml"), "management", "endpoint", "health", "probes", "enabled"))
                .as("management.endpoint.health.probes.enabled")
                .isEqualTo(true);
    }

    /**
     * Actuator обязан жить на том же порту, что и приложение: наружу платформа
     * публикует только один, и health на 8081 никто не найдёт.
     */
    @Test
    void actuatorStaysOnTheApplicationPort() {
        for (String profile : new String[]{"application.yml", "application-prod.yml"}) {
            assertThat(at(yaml(profile), "management", "server", "port"))
                    .as("management.server.port в %s — actuator уехал на отдельный порт", profile)
                    .isNull();
        }
    }

    /** health и info должны отдаваться наружу, иначе проверять нечего. */
    @Test
    void healthEndpointIsExposed() {
        assertThat(String.valueOf(at(yaml("application.yml"),
                "management", "endpoints", "web", "exposure", "include")))
                .contains("health");
    }

    /**
     * SERVER_ADDRESS=0.0.0.0 в Dockerfile стоял «чтобы наверняка», а на деле
     * сужал привязку до IPv4: 0.0.0.0 — это wildcard IPv4, и только он. Без
     * переменной Tomcat открывает сокет на wildcard-адресе двойного стека.
     * Health-check платформы ходит на http://localhost:8080 изнутри контейнера,
     * и если localhost там резолвится в ::1, IPv4-only сокет для него закрыт.
     *
     * Смотрим оба файла: они обязаны совпадать, но проверка дешевле разбора.
     */
    @Test
    void dockerfilesDoNotPinTheBindAddress() {
        for (String file : new String[]{"../Dockerfile", "Dockerfile"}) {
            String withoutComments;
            try {
                withoutComments = Files.readAllLines(Path.of(file)).stream()
                        .filter(line -> !line.stripLeading().startsWith("#"))
                        .collect(java.util.stream.Collectors.joining("\n"));
            } catch (Exception e) {
                throw new IllegalStateException("Не удалось прочитать " + file, e);
            }
            assertThat(withoutComments)
                    .as("%s задаёт SERVER_ADDRESS — привязка сузится до одного стека", file)
                    .doesNotContain("SERVER_ADDRESS");
        }
    }

    /**
     * Точный путь /actuator/health открыт давно, а подпути — нет: до правки
     * /actuator/health/liveness отдавал 401, проверено запросом.
     */
    @Test
    void healthGroupsArePubliclyReachable() {
        assertThat(source("config/SecurityConfig.java"))
                .as("SecurityConfig не открывает группы health-эндпоинта")
                .contains("\"/actuator/health/**\"");

        assertThat(source("security/JwtAuthenticationFilter.java"))
                .as("JwtAuthenticationFilter должен пропускать группы health мимо себя")
                .contains("/actuator/health/");
    }
}
