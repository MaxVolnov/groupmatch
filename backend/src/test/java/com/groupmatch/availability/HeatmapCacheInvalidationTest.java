package com.groupmatch.availability;

import com.groupmatch.BaseIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кэш теплокарты обязан сбрасываться при смене настроек группы.
 *
 * Дефект: ключ кэша содержит {@code grp.version}, а версию двигали только
 * триггеры на availability. Владелец включал «показывать участников», обновлял
 * страницу — и до часа видел прежнюю анонимную теплокарту. Со стороны это
 * выглядит как настройка, которая не работает; воспроизводилось до правки
 * стабильно, а «чинилось» только FLUSHDB.
 *
 * Ключевой приём этих тестов — переименование пользователя напрямую в БД.
 * Имена в теплокарту попадают из {@code app_user.display_name} в момент
 * пересчёта, и ни на ключ кэша, ни на {@code grp.version} это переименование не
 * влияет. Значит, ответ со старым именем мог прийти только из кэша, а с новым —
 * только из пересчёта. Это прямая проверка, а не догадка по времени ответа.
 *
 * (То, что переименование само по себе оставляет в кэше устаревшее имя, —
 * отдельный дефект того же рода. Здесь он не чинится, а используется как
 * измерительный инструмент; вынесен в отчёт.)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HeatmapCacheInvalidationTest extends BaseIntegrationTest {

    private static final String EMAIL = "heatcache@groupmatch-test.io";
    private static final String PASSWORD = "HeatCache1!";
    private static final String ORIGINAL_NAME = "Heat Cache Owner";

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private String token;
    private String groupId;
    private Instant from;
    private Instant to;

    @BeforeAll
    void setUp() {
        cleanupUser(EMAIL);

        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", ORIGINAL_NAME), jsonHeaders()), Map.class);
        token = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                                jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();

        groupId = rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("title", "Heatmap cache group", "tzId", "UTC"),
                                authHeaders(token)), Map.class)
                .getBody().get("id").toString();

        // Окно фиксировано: оно входит в ключ кэша, и «плавающее» от
        // Instant.now() давало бы каждому запросу свой ключ — тест бы всегда
        // проходил, ничего не проверяя.
        from = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
        to = from.plus(1, ChronoUnit.DAYS);

        Instant slotStart = from.plus(10, ChronoUnit.HOURS);
        rest.exchange(url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "startsAt", slotStart.toString(),
                        "endsAt", slotStart.plus(1, ChronoUnit.HOURS).toString()
                ), authHeaders(token)), Map.class);
    }

    @AfterAll
    void tearDown() {
        cleanupUser(EMAIL);
    }

    /** ① Включили настройку — имена появились в следующем же ответе. */
    @Test
    void enablingShowParticipantsRevealsNamesWithoutWaitingForTtl() {
        resetTo(false);
        assertThat(namesFromHeatmap())
                .as("при выключенной настройке имён быть не должно")
                .isNull();

        int versionBefore = groupVersion();
        setShowParticipants(true);

        assertThat(groupVersion())
                .as("смена настройки обязана сдвинуть версию — на ней держится ключ кэша")
                .isGreaterThan(versionBefore);
        assertThat(namesFromHeatmap())
                .as("имена должны прийти сразу, без ожидания TTL и без FLUSHDB")
                .containsExactly(ORIGINAL_NAME);
    }

    /** ② Выключили — имена пропали так же сразу. */
    @Test
    void disablingShowParticipantsHidesNamesWithoutWaitingForTtl() {
        resetTo(true);
        assertThat(namesFromHeatmap()).containsExactly(ORIGINAL_NAME);

        int versionBefore = groupVersion();
        setShowParticipants(false);

        assertThat(groupVersion()).isGreaterThan(versionBefore);
        assertThat(namesFromHeatmap())
                .as("выключение настройки не должно ждать TTL: иначе имена ещё час видны тем, "
                        + "от кого их только что закрыли")
                .isNull();
    }

    /**
     * ③ Правка поля, до теплокарты не доходящего, кэш не трогает.
     *
     * Иначе смысл кэша теряется: переименование группы выбрасывало бы час
     * работы. Из полей группы в ответ теплокарты попадает только
     * show_participants — остальное (title, description, tz_id, is_locked)
     * не читается там вовсе.
     */
    @Test
    void renamingGroupKeepsCacheWarm() {
        resetTo(true);
        assertThat(namesFromHeatmap()).containsExactly(ORIGINAL_NAME);

        int versionBefore = groupVersion();

        // Имя в БД меняется мимо кэша: пока ответ приходит со старым, он
        // приходит из кэша.
        rename("Renamed Behind The Cache");
        updateGroup(Map.of("title", "Heatmap cache group renamed"));

        assertThat(groupVersion())
                .as("переименование группы на теплокарту не влияет и версию двигать не должно")
                .isEqualTo(versionBefore);
        assertThat(namesFromHeatmap())
                .as("кэш должен пережить правку названия")
                .containsExactly(ORIGINAL_NAME);

        rename(ORIGINAL_NAME);
    }

    /**
     * ④ Кэш вообще работает: второй такой же запрос не пересчитывает агрегат.
     *
     * Две независимые проверки на один факт. Первая — по содержимому: имя
     * поменяно в БД, а в ответе остаётся прежнее, значит агрегат не собирали
     * заново. Вторая — по счётчику подготовленных SQL-запросов Hibernate:
     * промах обязан стоить дороже попадания.
     */
    @Test
    void secondIdenticalRequestIsServedFromCache() {
        resetTo(true);

        // Гарантированный промах: версия только что сдвинулась, ключ новый.
        long cold = measureHeatmap();

        rename("Changed While Cached");
        long warm = measureHeatmap();

        System.out.printf("SQL на теплокарту: промах %d, попадание %d%n", cold, warm);

        assertThat(namesFromHeatmap())
                .as("ответ обязан прийти из кэша — со старым именем, а не с новым")
                .containsExactly(ORIGINAL_NAME);
        assertThat(warm)
                .as("попадание в кэш обязано стоить меньше запросов, чем промах (%d)", cold)
                .isLessThan(cold);

        rename(ORIGINAL_NAME);
    }

    /**
     * ⑤ Ответ на саму правку настройки не врёт про версию.
     *
     * Версию проставляет триггер, а сущность в памяти об этом не знает —
     * без перечитывания эндпоинт отдавал число на единицу меньше, чем лежит
     * в базе. Сегодня фронтенд это поле не читает, но отдавать заведомо
     * неверное значение в публичном ответе всё равно нельзя.
     */
    @Test
    void updateResponseCarriesTheVersionThatWasActuallyStored() {
        resetTo(false);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/groups/" + groupId), HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", currentTitle(), "showParticipants", true),
                        authHeaders(token)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) resp.getBody().get("version")).intValue())
                .as("версия в ответе обязана совпадать с записанной в БД")
                .isEqualTo(groupVersion());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Приводит группу к известному состоянию и оставляет версию, с которой
     * теплокарту ещё ни разу не собирали: два переключения подряд двигают её
     * дважды, каким бы ни было исходное значение.
     *
     * Без этого тесты зависели бы от порядка выполнения — а он у JUnit не тот,
     * в котором методы написаны. На этом и попался замер: «холодный» запрос
     * оказывался попаданием в кэш, и обе цифры выходили одинаковыми.
     */
    private void resetTo(boolean showParticipants) {
        rename(ORIGINAL_NAME);
        setShowParticipants(!showParticipants);
        setShowParticipants(showParticipants);
    }

    /** Имена из первой непустой ячейки; null — если группа их не показывает. */
    @SuppressWarnings("unchecked")
    private List<String> namesFromHeatmap() {
        ResponseEntity<Map> resp = rest.exchange(heatmapUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> slots = (List<Map<String, Object>>) resp.getBody().get("slots");
        assertThat(slots).as("в окне обязан быть хотя бы один занятый бакет").isNotEmpty();
        return (List<String>) slots.get(0).get("displayNames");
    }

    private long measureHeatmap() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        rest.exchange(heatmapUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        return stats.getPrepareStatementCount();
    }

    private String heatmapUrl() {
        return url("/api/v1/groups/" + groupId + "/availability/heatmap"
                + "?from=" + from + "&to=" + to + "&granularityMinutes=30");
    }

    private int groupVersion() {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM grp WHERE id = ?::uuid", Integer.class, groupId);
    }

    private void setShowParticipants(boolean value) {
        updateGroup(Map.of("showParticipants", value));
    }

    /** PUT — полная замена, поэтому title обязателен даже при правке одного поля. */
    private void updateGroup(Map<String, Object> fields) {
        Map<String, Object> body = new HashMap<>(fields);
        body.putIfAbsent("title", currentTitle());
        rest.exchange(url("/api/v1/groups/" + groupId), HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), Map.class);
    }

    private String currentTitle() {
        return jdbcTemplate.queryForObject(
                "SELECT title FROM grp WHERE id = ?::uuid", String.class, groupId);
    }

    private void rename(String displayName) {
        jdbcTemplate.update("UPDATE app_user SET display_name = ? WHERE email = ?", displayName, EMAIL);
    }
}
