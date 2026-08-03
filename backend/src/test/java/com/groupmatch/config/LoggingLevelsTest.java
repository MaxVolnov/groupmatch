package com.groupmatch.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1: в проде логи писались на DEBUG — `com.groupmatch` и
 * `org.springframework.security` стояли на DEBUG прямо в дефолтах
 * application.yml, а prod-профиля не было вовсе.
 *
 * Проверяем содержимое конфигов, а не поднятый контекст: чтобы убедиться в
 * уровнях «по-настоящему», пришлось бы стартовать Spring с профилем prod —
 * это отдельный контекст ради трёх строк YAML. Регрессия здесь ровно одна:
 * кто-то снова кладёт DEBUG туда, где он действует по умолчанию.
 */
public class LoggingLevelsTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loggingLevels(String resource) {
        try (InputStream in = LoggingLevelsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("нет файла %s", resource).isNotNull();
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> logging = (Map<String, Object>) root.get("logging");
            if (logging == null) return Collections.emptyMap();
            Map<String, Object> level = (Map<String, Object>) logging.get("level");
            return level == null ? Collections.emptyMap() : level;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось прочитать " + resource, e);
        }
    }

    /** Главная защита: дефолт действует, когда SPRING_PROFILES_ACTIVE забыли. */
    @Test
    void defaultsAreSafeForProduction() {
        Map<String, Object> levels = loggingLevels("application.yml");
        assertThat(levels).isNotEmpty();
        levels.forEach((logger, level) ->
                assertThat(String.valueOf(level))
                        .as("логгер %s в дефолтах application.yml", logger)
                        .isNotEqualToIgnoringCase("DEBUG")
                        .isNotEqualToIgnoringCase("TRACE"));
    }

    @Test
    void prodProfileExistsAndPinsLevelsExplicitly() {
        Map<String, Object> levels = loggingLevels("application-prod.yml");
        assertThat(levels).containsKeys("root", "com.groupmatch", "org.springframework.security");
        levels.forEach((logger, level) ->
                assertThat(String.valueOf(level))
                        .as("логгер %s в prod-профиле", logger)
                        .isNotEqualToIgnoringCase("DEBUG")
                        .isNotEqualToIgnoringCase("TRACE"));
    }

    /** DEBUG никуда не делся — он просто переехал туда, где безопасен. */
    @Test
    void devProfileKeepsDebugForLocalWork() {
        assertThat(loggingLevels("application-dev.yml"))
                .containsEntry("com.groupmatch", "DEBUG");
    }
}
