package com.groupmatch.config;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Служебные пути actuator не должны открываться гостевым токеном.
 *
 * ОТКУДА ВЗЯЛОСЬ. `/actuator/metrics` был закрыт общим
 * {@code .anyRequest().authenticated()}, то есть требовал просто валидный
 * токен. А токен у нас раздаёт публичный {@code POST /api/v1/auth/guest} — без
 * почты, без пароля, за один анонимный запрос. Итого: любой желающий получал
 * список uri-шаблонов всех эндпоинтов (включая {@code /api/v1/admin/**}),
 * состав цепочки фильтров Spring Security, состояние пула соединений, heap,
 * свободное место и аптайм процесса.
 *
 * Гостевой вход низкопороговый намеренно и здесь не трогается — чинится
 * сторона actuator'а.
 *
 * ЗАЧЕМ ИМЕННО ИНТЕГРАЦИОННЫЙ ТЕСТ. Проверка конфига в
 * {@link ActuatorHealthConfigTest} говорит, какие эндпоинты объявлены; она не
 * говорит, кому они доступны. Ответ на второй вопрос даёт только реальный
 * запрос через полную цепочку фильтров — с токеном, полученным штатным путём,
 * а не подделанным в тесте.
 */
public class ActuatorAccessTest extends BaseIntegrationTest {

    /**
     * Токен добывается ровно так же, как его добыл бы посторонний: один POST на
     * публичный эндпоинт. Никаких тестовых заготовок и прямых вставок в БД —
     * иначе проверялся бы не тот путь, которым реально пользуются.
     */
    private String guestToken() {
        ResponseEntity<Map> response = rest.exchange(
                url("/api/v1/auth/guest"), HttpMethod.POST,
                new HttpEntity<>(Map.of("displayName", "Actuator Probe"), jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode().value())
                .as("гостевой вход должен остаться публичным и рабочим")
                .isEqualTo(201);

        String token = (String) response.getBody().get("accessToken");
        assertThat(token).as("гостевой access-токен").isNotBlank();
        return token;
    }

    /** Код ответа на GET, без исключений: 4xx и 5xx нас интересуют наравне с 2xx. */
    private int get(String path, String token) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(
                    token == null ? jsonHeaders() : authHeaders(token));
            return rest.exchange(url(path), HttpMethod.GET, entity, String.class)
                    .getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Неожиданная ошибка на " + path + ": " + e);
        }
    }

    /**
     * Главная проверка. 403, а не 404 и не 500: отказ обязан приходить от
     * авторизации, до диспетчера. Иначе результат зависел бы от того, объявлен
     * ли эндпоинт в exposure.include, — а это ровно та строка, которую легко
     * вернуть обратно.
     */
    @Test
    void guestTokenGetsNoMetrics() {
        String token = guestToken();

        assertThat(get("/actuator/metrics", token))
                .as("гостевой токен не должен открывать /actuator/metrics")
                .isEqualTo(403);

        assertThat(get("/actuator/metrics/jvm.memory.used", token))
                .as("отдельная метрика — тот же отказ, что и список")
                .isEqualTo(403);
    }

    /**
     * Дыра была не в одном пути, а в правиле «достаточно быть
     * аутентифицированным». Проверяем весь служебный префикс: heapdump — это
     * дамп памяти со всем её содержимым, env и configprops — конфигурация
     * вместе с адресами и именами пользователей.
     */
    @Test
    void guestTokenGetsNothingElseFromActuator() {
        String token = guestToken();

        for (String path : new String[]{
                "/actuator",
                "/actuator/info",
                "/actuator/env",
                "/actuator/beans",
                "/actuator/configprops",
                "/actuator/loggers",
                "/actuator/mappings",
                "/actuator/threaddump",
                "/actuator/heapdump",
                "/actuator/prometheus"}) {
            assertThat(get(path, token))
                    .as("гостевой токен на %s", path)
                    .isEqualTo(403);
        }
    }

    /** Без токена — 401: анонима отбивает точка входа, а не проверка роли. */
    @Test
    void anonymousGetsNoMetrics() {
        assertThat(get("/actuator/metrics", null))
                .as("аноним на /actuator/metrics")
                .isEqualTo(401);
    }

    /**
     * Обратная сторона правки: платформа опрашивает /actuator/health/liveness
     * без всякой авторизации, и если он начнёт отдавать 401 или 403, Timeweb
     * убьёт контейнер при полностью живом приложении. Именно так это уже
     * ломалось — разбор в шапке {@link ActuatorHealthConfigTest}.
     */
    @Test
    void platformProbesStayPublic() {
        assertThat(get("/actuator/health/liveness", null))
                .as("/actuator/health/liveness без авторизации — на него смотрит платформа")
                .isEqualTo(200);

        assertThat(get("/actuator/health/readiness", null))
                .as("/actuator/health/readiness без авторизации")
                .isEqualTo(200);
    }

    /**
     * Агрегат health тоже остаётся публичным: на него завязана проверка после
     * деплоя из docs/prod-runbook.md. Заодно убеждаемся, что деталей в ответе
     * нет — show-details: never в силе.
     *
     * Проверяем доступность, а не UP/DOWN. Агрегат складывает все индикаторы, и
     * в тестовом окружении он честно DOWN: почтового сервера на localhost:2525
     * нет, MailHealthIndicator это видит. Требовать здесь 200 значило бы
     * привязать проверку доступа к набору поднятых зависимостей — тест начал бы
     * падать по причинам, к правам доступа отношения не имеющим. Значимо
     * другое: код не 401 и не 403, то есть до авторизации дело не дошло.
     */
    @Test
    void healthAggregateStaysPublicWithoutDetails() {
        int status;
        String body;
        try {
            ResponseEntity<String> response = rest.exchange(
                    url("/actuator/health"), HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()), String.class);
            status = response.getStatusCode().value();
            body = response.getBody();
        } catch (HttpStatusCodeException e) {
            status = e.getStatusCode().value();
            body = e.getResponseBodyAsString();
        }

        assertThat(status)
                .as("/actuator/health без авторизации не должен упираться в права")
                .isNotIn(401, 403);

        // Ровно эти строки и утекали, когда стояло show-details: always —
        // имя СУБД, версия Valkey, пути и свободное место на диске.
        assertThat(body)
                .as("детали компонентов не должны попадать наружу")
                .doesNotContain("components")
                .doesNotContain("diskSpace")
                .doesNotContain("PostgreSQL")
                .doesNotContain("version");
    }
}
