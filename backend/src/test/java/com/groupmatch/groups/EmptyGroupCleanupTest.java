package com.groupmatch.groups;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа без участников удаляется вместе со своими данными.
 *
 * Пустая группа никому не видна и не нужна, а строки остаются: слоты, встречи,
 * приглашения. Через год это чистят руками по ночам, поэтому проверяется не
 * только исчезновение самой группы, но и то, что за ней ушло.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EmptyGroupCleanupTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL  = "empty-owner@groupmatch-test.io";
    private static final String MEMBER_EMAIL = "empty-member@groupmatch-test.io";
    private static final String PASSWORD     = "EmptyGroup1!";

    String ownerToken;
    String memberToken;
    String memberId;

    @BeforeAll
    void setUp() {
        for (String email : List.of(OWNER_EMAIL, MEMBER_EMAIL)) cleanupUser(email);
        signup(OWNER_EMAIL, "Empty Owner");
        memberId = signup(MEMBER_EMAIL, "Empty Member");
        ownerToken = signin(OWNER_EMAIL);
        memberToken = signin(MEMBER_EMAIL);
    }

    // ── 6. вышел не последний — группа цела ───────────────────────────────────

    @Test @Order(1)
    void groupSurvivesWhenSomeoneRemains() {
        String groupId = createGroup("Survives");
        addMember(groupId, memberId);

        leave(groupId, memberToken, memberId);

        assertThat(groupExists(groupId))
                .as("владелец остался — группе жить")
                .isTrue();
        assertThat(activeMembers(groupId)).isEqualTo(1);
    }

    /**
     * Владелец выйти из собственной группы не может — ни последним, ни при
     * живых участниках: {@code removeMember} отвечает 403 {@code not_group_owner}
     * на любую попытку. Обнаружено этим тестом, изначально он ходил именно так.
     *
     * Следствие: обычным выходом группу до нуля участников довести нельзя,
     * потому что владелец в ней остаётся всегда. Проверяем, что правило и
     * вправду такое, — иначе следующий тест доказывал бы не то, что кажется.
     */
    @Test @Order(2)
    void ownerCannotLeaveTheirOwnGroup() {
        String groupId = createGroup("Owner stuck");

        assertThat(leaveStatus(groupId, ownerToken, ownerId()))
                .as("владелец пытается выйти из своей группы")
                .isEqualTo(403);
        assertThat(groupExists(groupId)).isTrue();
    }

    // ── 5. ушёл последний — группа и её данные удалены ────────────────────────
    //
    // Раз владелец выйти не может, единственный реальный путь довести группу до
    // нуля участников — удаление аккаунта единственного участника. Он и
    // проверяется: важно не то, каким запросом это вызвано, а что за группой
    // ушли её данные.

    @Test @Order(3)
    void groupAndItsDataAreDeletedWhenTheLastMemberIsGone() {
        String groupId = createGroup("Disappears");

        addAvailability(groupId, ownerToken);
        addMeeting(groupId, ownerToken);
        addInvite(groupId, ownerToken);

        assertThat(countIn("availability", groupId)).isPositive();
        assertThat(countIn("meeting", groupId)).isPositive();
        assertThat(countIn("invite", groupId)).isPositive();

        rest.exchange(url("/api/v1/me"), HttpMethod.DELETE,
                new HttpEntity<>(Map.of("password", PASSWORD), authHeaders(ownerToken)), Void.class);

        assertThat(groupExists(groupId)).as("группа опустела и удалена").isFalse();
        assertThat(countIn("availability", groupId)).as("слоты").isZero();
        assertThat(countIn("meeting", groupId)).as("встречи").isZero();
        assertThat(countIn("invite", groupId)).as("приглашения").isZero();
        assertThat(countIn("grp_member", groupId)).as("записи участников").isZero();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String ownerId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?", String.class, OWNER_EMAIL);
    }

    private String createGroup(String title) {
        return rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", title, "tzId", "UTC"), authHeaders(ownerToken)), Map.class)
                .getBody().get("id").toString();
    }

    private void addMember(String groupId, String userId) {
        rest.exchange(url("/api/v1/groups/" + groupId + "/members"), HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", userId), authHeaders(ownerToken)), Map.class);
    }

    private void leave(String groupId, String token, String userId) {
        rest.exchange(url("/api/v1/groups/" + groupId + "/members/" + userId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Void.class);
    }

    /** Тот же выход, но с кодом ответа вместо исключения. */
    private int leaveStatus(String groupId, String token, String userId) {
        try {
            return rest.exchange(url("/api/v1/groups/" + groupId + "/members/" + userId),
                    HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), Void.class)
                    .getStatusCode().value();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return e.getStatusCode().value();
        }
    }

    private void addAvailability(String groupId, String token) {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        rest.exchange(url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                new HttpEntity<>(Map.of("startsAt", start.toString(),
                        "endsAt", start.plus(1, ChronoUnit.HOURS).toString()), authHeaders(token)), Map.class);
    }

    private void addMeeting(String groupId, String token) {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        rest.exchange(url("/api/v1/groups/" + groupId + "/meetings"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Doomed meeting", "startsAt", start.toString(),
                        "endsAt", start.plus(1, ChronoUnit.HOURS).toString()), authHeaders(token)), Map.class);
    }

    private void addInvite(String groupId, String token) {
        rest.exchange(url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                new HttpEntity<>(Map.of("maxUses", 5), authHeaders(token)), Map.class);
    }

    private boolean groupExists(String groupId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM grp WHERE id = ?::uuid", Integer.class, groupId);
        return n != null && n > 0;
    }

    private int activeMembers(String groupId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM grp_member WHERE grp_id = ?::uuid AND status = 'ACTIVE'",
                Integer.class, groupId);
        return n == null ? 0 : n;
    }

    private int countIn(String table, String groupId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE grp_id = ?::uuid", Integer.class, groupId);
        return n == null ? 0 : n;
    }

    private String signup(String email, String name) {
        return rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD, "displayName", name),
                        jsonHeaders()), Map.class)
                .getBody().get("id").toString();
    }

    private String signin(String email) {
        return rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD), jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();
    }
}
