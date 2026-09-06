package com.groupmatch.auth;

import com.groupmatch.BaseIntegrationTest;
import com.groupmatch.job.AccountAnonymizationJob;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Удаление аккаунта: мягкое, с отсрочкой и обезличиванием по её истечении.
 *
 * Проверяется в первую очередь то, что нельзя увидеть по коду: что вход
 * действительно закрывается, что группы не остаются без владельца и что по
 * ответу на регистрацию нельзя понять, был ли человек зарегистрирован.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AccountDeletionTest extends BaseIntegrationTest {

    private static final String PASSWORD = "DeleteMe1!";

    private static final String SOLO       = "del-solo@groupmatch-test.io";
    private static final String OWNER      = "del-owner@groupmatch-test.io";
    private static final String EARLY      = "del-early@groupmatch-test.io";
    private static final String LATE       = "del-late@groupmatch-test.io";
    private static final String LONELY     = "del-lonely@groupmatch-test.io";
    private static final String WRONG_PASS = "del-wrongpass@groupmatch-test.io";
    private static final String RESTORED   = "del-restored@groupmatch-test.io";
    private static final String ANONYMIZED = "del-anonymized@groupmatch-test.io";
    private static final String ADMIN      = "del-admin@groupmatch-test.io";

    @Autowired
    AccountAnonymizationJob anonymizationJob;

    String adminToken;

    /**
     * Обезличивание меняет адрес, поэтому после него строку можно найти только
     * по идентификатору. Запоминаем его заранее.
     */
    String anonymizedUserId;

    @BeforeAll
    void setUp() {
        for (String email : List.of(SOLO, OWNER, EARLY, LATE, LONELY, WRONG_PASS, RESTORED, ANONYMIZED, ADMIN)) {
            cleanupUser(email);
        }
        signup(ADMIN, "Deletion Admin");
        jdbcTemplate.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", ADMIN);
        adminToken = signin(ADMIN);
    }

    // ── 7. DELETE /me: отметка, отзыв токенов, вход закрыт ────────────────────

    @Test @Order(1)
    void deleteMeMarksAccountRevokesTokensAndBlocksSignin() {
        String userId = signup(SOLO, "Solo");
        String token = signin(SOLO);

        assertThat(deleteMe(token, PASSWORD)).isEqualTo(204);

        assertThat(deletedAt(SOLO)).as("deleted_at проставлен").isNotNull();
        assertThat(anonymizedAt(SOLO)).as("обезличивание ещё не наступило").isNull();

        assertThat(signinStatus(SOLO, PASSWORD))
                .as("вход под удалённым аккаунтом невозможен")
                .isEqualTo(401);

        assertThat(statusOf(url("/api/v1/me"), HttpMethod.GET, token))
                .as("выданный ранее токен больше не должен открывать сессию")
                .isIn(401, 403);

        assertThat(userId).isNotBlank();
    }

    /** Ответ на вход не должен отличаться от «такого адреса нет». */
    @Test @Order(2)
    void deletedAccountLooksExactlyLikeAMissingOne() {
        String deleted = bodyOfFailedSignin(SOLO, PASSWORD);
        String missing = bodyOfFailedSignin("no-such-user@groupmatch-test.io", PASSWORD);
        assertThat(deleted).isEqualTo(missing);
    }

    // ── 8. владелец с двумя участниками — владение уходит вступившему раньше ──

    @Test @Order(3)
    void ownershipGoesToTheEarliestJoinedMember() {
        String ownerId = signup(OWNER, "Owner");
        String earlyId = signup(EARLY, "Early Bird");
        String lateId = signup(LATE, "Late Comer");
        String ownerToken = signin(OWNER);

        String groupId = createGroup(ownerToken, "Inheritance");
        addMember(ownerToken, groupId, earlyId);
        addMember(ownerToken, groupId, lateId);

        assertThat(joinedAt(groupId, earlyId))
                .as("предпосылка теста: early вступил раньше late")
                .isBefore(joinedAt(groupId, lateId));

        assertThat(deleteMe(ownerToken, PASSWORD)).isEqualTo(204);

        assertThat(ownerOf(groupId))
                .as("владение переходит участнику с самой ранней датой вступления")
                .isEqualTo(earlyId);
        assertThat(roleOf(groupId, earlyId)).isEqualTo("OWNER");
        assertThat(statusOf(groupId, ownerId))
                .as("удалённый вышел из группы")
                .isEqualTo("LEFT");
    }

    // ── 15. удалённого не видно в списке участников ───────────────────────────

    @Test @Order(4)
    void deletedUserIsNotVisibleInMemberListForOthers() {
        String earlyToken = signin(EARLY);
        String groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM grp WHERE title = 'Inheritance'", String.class);

        ResponseEntity<List> members = rest.exchange(
                url("/api/v1/groups/" + groupId + "/members"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(earlyToken)), List.class);

        List<String> names = ((List<Map<String, Object>>) members.getBody()).stream()
                .map(m -> String.valueOf(m.get("displayName")))
                .toList();

        assertThat(names).doesNotContain("Owner");
        assertThat(names).contains("Early Bird", "Late Comer");
    }

    // ── 9. владелец без других участников — группа удаляется ──────────────────

    @Test @Order(5)
    void groupWithNoOtherMembersIsDeletedWithItsOwner() {
        signup(LONELY, "Lonely");
        String token = signin(LONELY);
        String groupId = createGroup(token, "Lonely Group");

        assertThat(deleteMe(token, PASSWORD)).isEqualTo(204);

        Integer groups = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM grp WHERE id = ?::uuid", Integer.class, groupId);
        assertThat(groups).as("группа без участников удалена").isZero();
    }

    // ── 10. регистрация на адрес удалённого — 409 без признака удаления ───────

    @Test @Order(6)
    void signupOnDeletedEmailIsIndistinguishableFromAnyTakenEmail() {
        String onDeleted = bodyOfFailedSignup(SOLO);
        String onLive = bodyOfFailedSignup(ADMIN);

        assertThat(signupStatus(SOLO)).isEqualTo(409);
        assertThat(onDeleted)
                .as("тело ответа не должно отличаться от обычного «адрес занят»")
                .isEqualTo(onLive);
        assertThat(onDeleted.toLowerCase())
                .doesNotContain("delet", "удал", "anonym");
    }

    // ── 11. администратор восстанавливает в пределах отсрочки ─────────────────

    @Test @Order(7)
    void adminRestoresAccountWithinGracePeriod() {
        signup(RESTORED, "To Be Restored");
        String token = signin(RESTORED);
        assertThat(deleteMe(token, PASSWORD)).isEqualTo(204);
        assertThat(signinStatus(RESTORED, PASSWORD)).isEqualTo(401);

        assertThat(adminRestore(userId(RESTORED))).isEqualTo(204);

        assertThat(deletedAt(RESTORED)).isNull();
        assertThat(signinStatus(RESTORED, PASSWORD))
                .as("после восстановления вход снова работает")
                .isEqualTo(200);
    }

    // ── 13. джоба обезличивания ───────────────────────────────────────────────

    @Test @Order(8)
    void anonymizationTouchesOnlyAccountsPastTheGracePeriod() {
        String userId = signup(ANONYMIZED, "To Be Anonymized");
        String token = signin(ANONYMIZED);
        String groupId = createGroup(token, "Anonymized Owner Group");

        // Второй участник, чтобы группа пережила удаление владельца и встреча
        // осталась кому-то нужна.
        String earlyId = userId(EARLY);
        addMember(token, groupId, earlyId);

        addAvailability(token, groupId);
        String meetingId = addMeeting(token, groupId);

        assertThat(deleteMe(token, PASSWORD)).isEqualTo(204);

        // Свежеудалённый — джоба его не трогает.
        anonymizationJob.anonymizeExpired();
        assertThat(anonymizedAtById(userId)).as("отсрочка ещё не истекла").isNull();
        assertThat(emailOf(userId)).isEqualTo(ANONYMIZED);

        // Состарим отметку и прогоним снова.
        jdbcTemplate.update("UPDATE app_user SET deleted_at = ? WHERE id = ?::uuid",
                java.sql.Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)), userId);
        anonymizationJob.anonymizeExpired();

        anonymizedUserId = userId;
        assertThat(anonymizedAtById(userId)).as("обезличен").isNotNull();
        assertThat(emailOf(userId))
                .as("адрес заменён на неиспользуемый")
                .isNotEqualTo(ANONYMIZED)
                .endsWith("@deleted.invalid");
        assertThat(displayNameOf(userId)).isEqualTo("Удалённый пользователь");

        Integer slots = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM availability WHERE user_id = ?::uuid", Integer.class, userId);
        assertThat(slots).as("личная доступность удалена").isZero();

        Integer meetings = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM meeting WHERE id = ?::uuid", Integer.class, meetingId);
        assertThat(meetings)
                .as("встреча цела: она принадлежит группе, а не автору")
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT creator_id FROM meeting WHERE id = ?::uuid", String.class, meetingId))
                .as("автор остаётся ссылкой на обезличенную строку")
                .isEqualTo(userId);
    }

    // ── 12. восстановление после обезличивания невозможно ─────────────────────

    @Test @Order(9)
    void restoreAfterAnonymizationIsRejected() {
        assertThat(adminRestore(anonymizedUserId))
                .as("восстанавливать нечего: исходных данных больше нет")
                .isEqualTo(400);
    }

    // ── 14. после обезличивания адрес свободен ────────────────────────────────

    @Test @Order(10)
    void originalEmailBecomesAvailableAfterAnonymization() {
        assertThat(signupStatus(ANONYMIZED))
                .as("адрес освободился вместе с обезличиванием")
                .isEqualTo(201);
        cleanupUser(ANONYMIZED);
    }

    // ── админское удаление ────────────────────────────────────────────────────

    @Test @Order(11)
    void adminDeletesSomeoneElsesAccount() {
        assertThat(signinStatus(RESTORED, PASSWORD)).isEqualTo(200);

        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/admin/users/" + userId(RESTORED)), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminToken)), Void.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        assertThat(deletedAt(RESTORED)).isNotNull();
        assertThat(signinStatus(RESTORED, PASSWORD)).isEqualTo(401);
    }

    /** Пароль обязателен: угнанная сессия не должна стирать аккаунт одним запросом. */
    @Test @Order(12)
    void deletionWithoutCorrectPasswordIsRejected() {
        signup(WRONG_PASS, "Wrong Password");
        String token = signin(WRONG_PASS);

        assertThat(deleteMe(token, "WrongPassword1!")).isEqualTo(401);
        assertThat(deleteMe(token, null)).isEqualTo(400);
        assertThat(deletedAt(WRONG_PASS)).as("аккаунт не тронут").isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int deleteMe(String token, String password) {
        try {
            Object body = password == null ? Map.of() : Map.of("password", password);
            return rest.exchange(url("/api/v1/me"), HttpMethod.DELETE,
                    new HttpEntity<>(body, authHeaders(token)), Void.class)
                    .getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Неожиданная ошибка при DELETE /me: " + e);
        }
    }

    private int adminRestore(String userId) {
        try {
            return rest.exchange(url("/api/v1/admin/users/" + userId + "/restore"), HttpMethod.PATCH,
                    new HttpEntity<>(authHeaders(adminToken)), Void.class)
                    .getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Неожиданная ошибка при restore: " + e);
        }
    }

    private int statusOf(String url, HttpMethod method, String token) {
        try {
            return rest.exchange(url, method, new HttpEntity<>(authHeaders(token)), String.class)
                    .getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Неожиданная ошибка: " + e);
        }
    }

    private int signinStatus(String email, String password) {
        try {
            return rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("email", email, "password", password), jsonHeaders()),
                    Map.class).getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Неожиданная ошибка при signin: " + e);
        }
    }

    private String bodyOfFailedSignin(String email, String password) {
        try {
            rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("email", email, "password", password), jsonHeaders()),
                    Map.class);
            return fail("Ожидали отказ на входе");
        } catch (HttpStatusCodeException e) {
            return withoutTimestamp(e.getResponseBodyAsString());
        }
    }

    private int signupStatus(String email) {
        try {
            return rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("email", email, "password", PASSWORD,
                            "displayName", "Someone New"), jsonHeaders()), Map.class)
                    .getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Неожиданная ошибка при signup: " + e);
        }
    }

    private String bodyOfFailedSignup(String email) {
        try {
            rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("email", email, "password", PASSWORD,
                            "displayName", "Someone New"), jsonHeaders()), Map.class);
            return fail("Ожидали 409 на занятый адрес");
        } catch (HttpStatusCodeException e) {
            return withoutTimestamp(e.getResponseBodyAsString());
        }
    }

    /** Метка времени различается всегда и к сравнению отношения не имеет. */
    private static String withoutTimestamp(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"—\"");
    }

    private String createGroup(String token, String title) {
        return rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", title, "tzId", "UTC"), authHeaders(token)), Map.class)
                .getBody().get("id").toString();
    }

    private void addMember(String token, String groupId, String userId) {
        rest.exchange(url("/api/v1/groups/" + groupId + "/members"), HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", userId), authHeaders(token)), Map.class);
    }

    private void addAvailability(String token, String groupId) {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        rest.exchange(url("/api/v1/groups/" + groupId + "/availability"), HttpMethod.POST,
                new HttpEntity<>(Map.of("startsAt", start.toString(),
                        "endsAt", start.plus(1, ChronoUnit.HOURS).toString()), authHeaders(token)), Map.class);
    }

    private String addMeeting(String token, String groupId) {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        return rest.exchange(url("/api/v1/groups/" + groupId + "/meetings"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Survives its author", "startsAt", start.toString(),
                        "endsAt", start.plus(1, ChronoUnit.HOURS).toString()), authHeaders(token)), Map.class)
                .getBody().get("id").toString();
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

    // ── чтение состояния из базы ──────────────────────────────────────────────

    private String userId(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?", String.class, email);
    }

    private Instant deletedAt(String email) {
        return timestamp("SELECT deleted_at FROM app_user WHERE email = ?", email);
    }

    private Instant anonymizedAt(String email) {
        return timestamp("SELECT anonymized_at FROM app_user WHERE email = ?", email);
    }

    /** По id, а не по адресу: обезличивание адрес меняет. */
    private Instant anonymizedAtById(String userId) {
        java.sql.Timestamp ts = jdbcTemplate.queryForObject(
                "SELECT anonymized_at FROM app_user WHERE id = ?::uuid",
                java.sql.Timestamp.class, userId);
        return ts == null ? null : ts.toInstant();
    }

    private Instant timestamp(String sql, String arg) {
        java.sql.Timestamp ts = jdbcTemplate.queryForObject(sql, java.sql.Timestamp.class, arg);
        return ts == null ? null : ts.toInstant();
    }

    private Instant joinedAt(String groupId, String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT joined_at FROM grp_member WHERE grp_id = ?::uuid AND user_id = ?::uuid",
                java.sql.Timestamp.class, groupId, userId).toInstant();
    }

    private String ownerOf(String groupId) {
        return jdbcTemplate.queryForObject(
                "SELECT owner_id FROM grp WHERE id = ?::uuid", String.class, groupId);
    }

    private String roleOf(String groupId, String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT role FROM grp_member WHERE grp_id = ?::uuid AND user_id = ?::uuid",
                String.class, groupId, userId);
    }

    private String statusOf(String groupId, String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM grp_member WHERE grp_id = ?::uuid AND user_id = ?::uuid",
                String.class, groupId, userId);
    }

    private String emailOf(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT email FROM app_user WHERE id = ?::uuid", String.class, userId);
    }

    private String displayNameOf(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT display_name FROM app_user WHERE id = ?::uuid", String.class, userId);
    }
}
