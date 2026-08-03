package com.groupmatch.meetings;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1: {@code MeetingService.createMeeting()} читал настройки уведомлений
 * через {@code getOrCreate()} в цикле по участникам — SELECT на каждого.
 * В группе на 30 человек это 30 лишних round-trip'ов на одно создание.
 *
 * Меряем не абсолютное число запросов (оно хрупкое и зависит от Hibernate),
 * а **прирост** при увеличении группы. Вставка уведомления на получателя
 * неизбежна — это отдельная строка; а вот чтение настроек должно стоить
 * один запрос независимо от размера группы.
 *
 * До фикса прирост был ~2 запроса на участника (SELECT настроек + INSERT
 * уведомления), после — ~1.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MeetingNPlusOneTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL = "nplusone-owner@groupmatch-test.io";
    private static final String PASSWORD = "NPlusOne1!";

    private static final int SMALL_GROUP = 2;
    private static final int LARGE_GROUP = 8;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private String ownerToken;
    private UUID smallGroupId;
    private UUID largeGroupId;
    private final List<UUID> fixtureUsers = new ArrayList<>();

    @BeforeAll
    void setUp() {
        cleanupUser(OWNER_EMAIL);
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", PASSWORD,
                        "displayName", "NPlusOne Owner"), jsonHeaders()), Map.class);
        ownerToken = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", PASSWORD),
                                jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();

        smallGroupId = createGroupWithMembers("N+1 small", SMALL_GROUP);
        largeGroupId = createGroupWithMembers("N+1 large", LARGE_GROUP);
    }

    @AfterAll
    void tearDown() {
        fixtureUsers.forEach(id -> jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", id));
        cleanupUser(OWNER_EMAIL);
    }

    @Test
    void preferencesLookupDoesNotScaleWithGroupSize() {
        // Прогрев: первое создание в каждой группе прогревает кэши Hibernate
        // и планы запросов, иначе замер ловит разовые накладные расходы.
        createMeeting(smallGroupId, 1);
        createMeeting(largeGroupId, 1);

        long smallQueries = measureCreateMeeting(smallGroupId, 2);
        long largeQueries = measureCreateMeeting(largeGroupId, 2);

        long extraMembers = LARGE_GROUP - SMALL_GROUP;
        long growth = largeQueries - smallQueries;

        System.out.printf("SQL на создание встречи: группа из %d → %d запросов, из %d → %d, прирост %d на %d участников%n",
                SMALL_GROUP, smallQueries, LARGE_GROUP, largeQueries, growth, extraMembers);

        // Допуск +2 к неизбежным вставкам уведомлений. До фикса прирост был бы
        // вдвое больше (ещё по SELECT'у настроек на каждого) и тест бы упал.
        assertThat(growth)
                .as("прирост запросов на %d участников: ожидаем ~вставки уведомлений, а не вставки+чтения",
                        extraMembers)
                .isLessThanOrEqualTo(extraMembers + 2);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long measureCreateMeeting(UUID groupId, int dayOffset) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        createMeeting(groupId, dayOffset);
        return stats.getPrepareStatementCount();
    }

    private void createMeeting(UUID groupId, int dayOffset) {
        Instant start = Instant.now().plus(dayOffset, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        rest.exchange(url("/api/v1/groups/" + groupId + "/meetings"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "title", "N+1 probe " + UUID.randomUUID(),
                        "startsAt", start.toString(),
                        "endsAt", start.plus(1, ChronoUnit.HOURS).toString()
                ), authHeaders(ownerToken)), Map.class);
    }

    private UUID createGroupWithMembers(String title, int memberCount) {
        UUID groupId = UUID.fromString(rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                        new HttpEntity<>(Map.of("title", title, "tzId", "UTC"),
                                authHeaders(ownerToken)), Map.class)
                .getBody().get("id").toString());

        for (int i = 0; i < memberCount; i++) {
            UUID userId = insertUser();
            fixtureUsers.add(userId);
            rest.exchange(url("/api/v1/groups/" + groupId + "/members"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("userId", userId.toString()), authHeaders(ownerToken)),
                    Map.class);
            // Настройки заводим заранее: иначе первое создание встречи ещё и
            // вставляет их, и замер смешивает две разные причины роста.
            jdbcTemplate.update("""
                    INSERT INTO notification_preferences
                        (user_id, email_member_joined, email_meeting_reminder,
                         inapp_member_joined, inapp_meeting_created)
                    VALUES (?, true, true, true, true)
                    ON CONFLICT (user_id) DO NOTHING
                    """, userId);
        }
        return groupId;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, email, password_hash, display_name, tz_id, locale, plan, role,
                     is_guest, is_banned, is_email_verified, is_blocked,
                     created_at, updated_at, last_activity_at)
                VALUES (?, ?, 'x', 'N+1 Member', 'UTC', 'ru', 'FREE', 'USER',
                        false, false, true, false, ?, ?, ?)
                """,
                id, "nplusone-" + id + "@groupmatch-test.io",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
        return id;
    }
}
