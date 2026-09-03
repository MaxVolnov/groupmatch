package com.groupmatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка защиты от старта прода с публичным JWT-секретом.
 *
 * Контекст поднимается {@link ApplicationContextRunner}, а не {@code @SpringBootTest}:
 * проверяется одно решение — «стартовать или нет», — и для него не нужны ни
 * база, ни Redis. Заодно негативный случай не требует поднимать заведомо
 * обречённый полный контекст с Testcontainers.
 *
 * Цена этого выбора: {@code withUserConfiguration} регистрирует бин напрямую,
 * мимо сканирования компонентов, поэтому опечатка в {@code @Component} или в
 * имени профиля здесь не всплывёт. Это закрыто отдельной проверкой по исходнику
 * — тем же приёмом, что в {@link ActuatorHealthConfigTest}.
 */
public class JwtSecretGuardTest {

    /** Из backend/src/main/resources/application.yml. */
    private static final String DEV_DEFAULT =
            "dev-secret-key-change-in-production-min-256-bits-12345678901234567890";

    private static final String REAL_SECRET =
            "b3JlY2FzdC10aGUtd2VhdGhlci1ub3QtdGhlLWNsaW1hdGUtMjAyNi14eXo";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(JwtSecretGuard.class);

    // ─── Три случая из постановки ─────────────────────────────────────────────

    @Test
    void prodWithCommittedDefaultDoesNotStart() {
        runner.withPropertyValues("spring.profiles.active=prod", "jwt.secret=" + DEV_DEFAULT)
                .run(context -> {
                    assertThat(context).hasFailed();

                    String message = failureText(context.getStartupFailure());
                    assertThat(message)
                            .as("сообщение обязано называть переменную")
                            .contains("JWT_SECRET");
                    assertThat(message)
                            .as("и не должно печатать сам секрет ни в каком виде")
                            .doesNotContain(DEV_DEFAULT);
                });
    }

    @Test
    void prodWithOwnSecretStarts() {
        runner.withPropertyValues("spring.profiles.active=prod", "jwt.secret=" + REAL_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtSecretGuard.class);
                });
    }

    /**
     * Локальная разработка не должна ломаться: дефолт в {@code application.yml}
     * остаётся, и без профиля prod проверка вообще не создаётся.
     */
    @Test
    void withoutProdProfileTheDefaultIsFine() {
        runner.withPropertyValues("jwt.secret=" + DEV_DEFAULT)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .as("вне prod проверки быть не должно вовсе")
                            .doesNotHaveBean(JwtSecretGuard.class);
                });
    }

    // ─── Остальные способы остаться без секрета ───────────────────────────────

    @Test
    void prodWithBlankSecretDoesNotStart() {
        for (String value : new String[]{"", "   "}) {
            runner.withPropertyValues("spring.profiles.active=prod", "jwt.secret=" + value)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(failureText(context.getStartupFailure()))
                                .contains("JWT_SECRET", "не задан");
                    });
        }
    }

    /**
     * Короткий ключ jjwt тоже не примет, но лениво — на первой подписи токена,
     * то есть на первом входе живого пользователя, уже в проде.
     */
    @Test
    void prodWithShortSecretDoesNotStart() {
        String short31 = "a".repeat(JwtSecretGuard.MIN_SECRET_BYTES - 1);
        runner.withPropertyValues("spring.profiles.active=prod", "jwt.secret=" + short31)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(failureText(context.getStartupFailure()))
                            .contains("короче " + JwtSecretGuard.MIN_SECRET_BYTES);
                });
    }

    @Test
    void secretOfExactlyMinimumLengthIsAccepted() {
        assertThat(JwtSecretGuard.validate("b".repeat(JwtSecretGuard.MIN_SECRET_BYTES))).isNull();
    }

    // ─── Список запрещённых значений не должен отстать от файлов ──────────────

    /**
     * Самая нужная проверка в этом классе. Список в {@code COMMITTED_DEFAULTS}
     * — единственное место, которое можно забыть обновить, добавив четвёртый
     * дефолт в четвёртый файл. Поэтому дефолты вычитываются из самих файлов.
     */
    @Test
    void everyCommittedSecretIsOnTheForbiddenList() {
        assertThat(defaultFromYaml("../backend/src/main/resources/application.yml"))
                .as("дефолт из application.yml")
                .isIn(JwtSecretGuard.COMMITTED_DEFAULTS);

        assertThat(defaultFromYaml("../backend/src/test/resources/application-test.yml"))
                .as("дефолт из application-test.yml")
                .isIn(JwtSecretGuard.COMMITTED_DEFAULTS);

        assertThat(match(read("../.github/workflows/ci.yml"), "JWT_SECRET:\\s*(\\S+)"))
                .as("значение из ci.yml")
                .isIn(JwtSecretGuard.COMMITTED_DEFAULTS);
    }

    /**
     * Гейт по профилю объявлен аннотацией, а не кодом, поэтому опечатка в имени
     * профиля выключила бы проверку молча: контекст поднялся бы, тесты выше —
     * они регистрируют бин руками — остались бы зелёными.
     */
    @Test
    void theGuardIsWiredIntoTheApplication() {
        String source = read("../backend/src/main/java/com/groupmatch/config/JwtSecretGuard.java");
        assertThat(source).as("бин должен подхватываться сканированием").contains("@Component");
        assertThat(source).as("и включаться ровно в профиле prod").contains("@Profile(\"prod\")");
    }

    // ─── Вспомогательное ──────────────────────────────────────────────────────

    /** Сообщение вместе со всеми обёртками Spring — секрет не должен всплыть ни в одной. */
    private static String failureText(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
        }
        return text.toString();
    }

    /** Достаёт значение по умолчанию из строки вида {@code secret: ${JWT_SECRET:…}}. */
    private static String defaultFromYaml(String path) {
        return match(read(path), "\\$\\{JWT_SECRET:([^}]*)}");
    }

    private static String match(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertThat(m.find()).as("не нашли JWT_SECRET по шаблону %s", regex).isTrue();
        return m.group(1);
    }

    /**
     * Пути от рабочей директории Gradle (backend/), поэтому с «../». Тот же
     * приём, что в ActuatorHealthConfigTest для корневого Dockerfile.
     */
    private static String read(String relative) {
        try {
            return Files.readString(Path.of(relative));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось прочитать " + relative, e);
        }
    }
}
