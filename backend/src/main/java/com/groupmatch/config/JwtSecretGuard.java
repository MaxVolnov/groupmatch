package com.groupmatch.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Не даёт проду стартовать с секретом, который знает кто угодно.
 *
 * ОТКУДА ВЗЯЛОСЬ. {@code application.yml} задаёт
 * {@code jwt.secret: ${JWT_SECRET:dev-secret-key-…}} — значение по умолчанию
 * нужно, чтобы поднимался локальный стенд, и оно лежит в репозитории открытым
 * текстом. Проверки при старте не было никакой. Если переменная не доезжала до
 * контейнера — опечатка в дашборде, пересоздание приложения, переезд на другую
 * площадку, — приложение **успешно стартовало** и начинало подписывать токены
 * публичным секретом. Снаружи не менялось ничего: health зелёный, логи чистые,
 * люди входят. При этом кто угодно выпускает себе токен с любым {@code sub} и
 * ролью {@code ADMIN}. Вероятность низкая, цена срабатывания — полный обход
 * аутентификации, обнаружение — случайное. Ровно тот класс дефекта, ради
 * которого делают fail-fast.
 *
 * ПОЧЕМУ ПРОВЕРЯЕТСЯ ЗНАЧЕНИЕ, А НЕ «ПРИШЛА ЛИ ПЕРЕМЕННАЯ ИЗ ОКРУЖЕНИЯ».
 * Способ доставки может смениться (файл, параметр командной строки, секрет
 * платформы), и проверка «есть ли JWT_SECRET в systemEnvironment» тогда
 * запретила бы законный вариант. Опасен не способ доставки, а публичность
 * значения — её и проверяем.
 *
 * ПОЧЕМУ {@code @Profile("prod")}, а не проверка профиля внутри. Гейт
 * объявлен там же, где видно: локальная разработка и тесты этого бина просто
 * не получают, и ломать их нечем. Обратная сторона — опечатка в имени профиля
 * тихо выключит проверку, поэтому написание закреплено тестом
 * {@code JwtSecretGuardTest}.
 */
@Component
@Profile("prod")
@Slf4j
public class JwtSecretGuard {

    /**
     * HS256 требует ключ не короче 256 бит. Jjwt это тоже проверяет, но лениво —
     * {@code Keys.hmacShaKeyFor} бросает на первой подписи токена, то есть уже в
     * рантайме, на первом входе живого пользователя. Здесь — на старте.
     */
    static final int MIN_SECRET_BYTES = 32;

    /**
     * Значения, лежащие в этом репозитории открытым текстом. Секретами они не
     * являются по построению, поэтому в проде запрещены все три.
     *
     * Перечислять их здесь не утечка: они и так в трёх файлах рядом. Список
     * держится в синхронизации тестом — тот вычитывает дефолты из самих файлов
     * и падает, если появился четвёртый.
     */
    static final Set<String> COMMITTED_DEFAULTS = Set.of(
            // backend/src/main/resources/application.yml
            "dev-secret-key-change-in-production-min-256-bits-12345678901234567890",
            // backend/src/test/resources/application-test.yml
            "test-secret-key-must-be-at-least-256-bits-long-padding-padding",
            // .github/workflows/ci.yml
            "ci-test-secret-key-minimum-256-bits-long-padding-here-x"
    );

    public JwtSecretGuard(@Value("${jwt.secret:}") String secret) {
        String problem = validate(secret);
        if (problem == null) {
            log.info("JWT_SECRET: задан собственный секрет, проверка пройдена");
            return;
        }

        // Пишем в лог отдельно от исключения: в логах платформы это окажется
        // последней осмысленной строкой перед стектрейсом, и разбираться по ней
        // быстрее, чем по обёрткам BeanCreationException.
        log.error("Старт прерван: {}", problem);
        throw new IllegalStateException(problem + " " + REMEDY);
    }

    private static final String REMEDY =
            "Задайте переменную окружения JWT_SECRET (не короче " + MIN_SECRET_BYTES
            + " байт) и перезапустите приложение. Смена секрета разлогинивает всех "
            + "разом — это ожидаемо. Само значение здесь не печатается намеренно.";

    /**
     * @return описание проблемы или {@code null}, если секрет пригоден.
     *         Ни одна из веток не включает само значение в текст.
     */
    static String validate(String secret) {
        if (secret == null || secret.isBlank()) {
            return "JWT_SECRET не задан, а в профиле prod подписывать токены нечем.";
        }
        if (COMMITTED_DEFAULTS.contains(secret)) {
            return "JWT_SECRET равен значению по умолчанию из репозитория — оно "
                    + "публично, и с ним кто угодно выпустит себе токен с ролью ADMIN.";
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            return "JWT_SECRET короче " + MIN_SECRET_BYTES + " байт, для HS256 этого мало.";
        }
        return null;
    }
}
