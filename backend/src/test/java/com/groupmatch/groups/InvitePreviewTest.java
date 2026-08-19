package com.groupmatch.groups;

import com.groupmatch.BaseIntegrationTest;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Публичное превью приглашения: {@code GET /api/v1/invites/{token}}.
 *
 * Экран /join/{token} открывается до входа, поэтому эндпоинт работает без
 * авторизации — и именно поэтому здесь важны две вещи: он не отдаёт лишнего и
 * его нельзя перебирать.
 *
 * Лимит занижен через {@code @TestPropertySource}, чтобы у теста были свои
 * бакеты и он не задевал остальной сьют.
 *
 * ⚠️ Значение 10, а не 5, — намеренный запас. Тесты 1–4 расходуют пять
 * запросов, и при лимите в пять отказ приходился ровно на границу: любая
 * добавленная проверка сдвигала бы 429 внутрь {@link #nameTaken(String)},
 * который {@code HttpClientErrorException} не перехватывает. Падение выглядело
 * бы как поломка name-taken, а не как исчерпанный бюджет, и следующий человек
 * потратил бы час не там. Смысл проверки от запаса не меняется — она про то,
 * что отказ вообще наступает и считается на клиента, а не на токен.
 */
@TestPropertySource(properties = {
        "app.rate-limit.invite-preview=10",
        "app.trusted-proxies="
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InvitePreviewTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL = "invite-preview-owner@groupmatch-test.io";
    private static final String PASSWORD = "SecurePass1!";
    private static final String OWNER_NAME = "Пригласивший";
    private static final String GROUP_TITLE = "Выпить пиво";

    /** Без ретраев: клиент из базового класса послушно ждёт по Retry-After. */
    private final RestTemplate noRetry = new RestTemplate(
            new HttpComponentsClientHttpRequestFactory(
                    HttpClients.custom().disableAutomaticRetries().build()));

    private String inviteToken;

    @Test @Order(1)
    void previewShowsWhoInvitesAndWhere() {
        cleanupUser(OWNER_EMAIL);
        String accessToken = signUp();
        String groupId = createGroup(accessToken);
        inviteToken = createInvite(accessToken, groupId);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = noRetry.getForObject(
                url("/api/v1/invites/" + inviteToken), Map.class);

        assertThat(body).isNotNull();
        assertThat(body.get("valid")).isEqualTo(true);
        assertThat(body.get("groupName")).isEqualTo(GROUP_TITLE);
        assertThat(body.get("inviterName")).isEqualTo(OWNER_NAME);
    }

    /**
     * Эндпоинт публичный, поэтому список полей — граница утечки, а не вопрос
     * вкуса. Идентификатор группы особенно: с ним посторонний получает адрес,
     * по которому можно стучаться дальше.
     */
    @Test @Order(2)
    void previewLeaksNothingBeyondTheTwoLabels() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = noRetry.getForObject(
                url("/api/v1/invites/" + inviteToken), Map.class);

        assertThat(body).isNotNull();
        assertThat(body.keySet())
                .as("в ответе только valid, groupName и inviterName")
                .containsExactlyInAnyOrder("valid", "groupName", "inviterName");
    }

    /**
     * 200, а не 404: этот же ответ идёт в превью ссылки у мессенджеров, где
     * 404 ломает карточку целиком и человек видит «битую ссылку» вместо
     * объяснения.
     */
    @Test @Order(3)
    void unknownTokenAnswersTwoHundredWithReason() {
        ResponseEntity<Map> response = noRetry.getForEntity(
                url("/api/v1/invites/definitely-not-a-real-token"), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("valid")).isEqualTo(false);
        assertThat(response.getBody().get("reason")).isEqualTo("not_found");
        assertThat(response.getBody())
                .as("у несуществующего приглашения нечего показывать")
                .doesNotContainKeys("groupName", "inviterName");
    }

    /** Подсказка про второй гостевой аккаунт: один бит по введённому имени. */
    @Test @Order(4)
    void nameTakenReportsExistingMember() {
        assertThat(nameTaken(OWNER_NAME)).as("имя владельца уже в группе").isTrue();
        assertThat(nameTaken("Кто-то совершенно другой")).isFalse();
    }

    /**
     * Главная причина, по которой лимит вообще понадобился: эндпоинт отвечает
     * 200 и на промах, значит по нему можно перебирать пространство токенов,
     * отличая попадание от промаха по одному полю ответа.
     *
     * Ловушка, из-за которой это чуть не осталось незакрытым: RateLimitFilter
     * начинался со строки «метод не POST — пропустить», и на GET не срабатывал
     * вовсе.
     */
    @Test @Order(5)
    void previewIsRateLimited() {
        // К этому моменту часть бюджета уже израсходована предыдущими тестами —
        // добираем до отказа и проверяем, что отказ наступает. Итераций с
        // запасом: их число не должно зависеть от того, сколько запросов сделали
        // тесты выше.
        int lastStatus = 200;
        for (int i = 1; i <= 15 && lastStatus != 429; i++) {
            lastStatus = get("/api/v1/invites/" + inviteToken);
        }
        assertThat(lastStatus).as("перебор с одного IP должен упереться в 429").isEqualTo(429);

        assertThat(get("/api/v1/invites/another-token-entirely"))
                .as("лимит на клиента, а не на токен — иначе перебор бесплатен")
                .isEqualTo(429);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private int get(String path) {
        try {
            return noRetry.getForEntity(url(path), Map.class).getStatusCode().value();
        } catch (HttpClientErrorException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Unexpected error: " + e.getMessage());
        }
    }

    private boolean nameTaken(String name) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = noRetry.getForObject(
                url("/api/v1/invites/" + inviteToken + "/name-taken?name={n}"), Map.class, name);
        assertThat(body).isNotNull();
        return Boolean.TRUE.equals(body.get("taken"));
    }

    private String signUp() {
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = rest.postForObject(url("/api/v1/auth/signup"),
                new HttpEntity<>(Map.of(
                        "email", OWNER_EMAIL,
                        "password", PASSWORD,
                        "displayName", OWNER_NAME,
                        // Поле в SignupRequest называется именно tzid, без
                        // заглавной: с "tzId" значение молча не биндится и
                        // зона берётся по умолчанию (см. docs/backlog.md, п. 10).
                        "tzid", "Europe/Moscow"), jsonHeaders()),
                Map.class);
        assertThat(auth).isNotNull();

        // POST /auth/signup отдаёт UserResponse, без токенов: за ними идём в
        // /auth/signin. Так же поступает InviteTest.
        @SuppressWarnings("unchecked")
        Map<String, Object> session = rest.postForObject(url("/api/v1/auth/signin"),
                new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", PASSWORD), jsonHeaders()),
                Map.class);
        assertThat(session).isNotNull();
        return (String) session.get("accessToken");
    }

    private String createGroup(String accessToken) {
        @SuppressWarnings("unchecked")
        Map<String, Object> group = rest.postForObject(url("/api/v1/groups"),
                new HttpEntity<>(Map.of("title", GROUP_TITLE, "tzId", "Europe/Moscow"),
                        authHeaders(accessToken)),
                Map.class);
        assertThat(group).isNotNull();
        return (String) group.get("id");
    }

    private String createInvite(String accessToken, String groupId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> invite = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                new HttpEntity<>(Map.of("maxUses", 0), authHeaders(accessToken)),
                Map.class).getBody();
        assertThat(invite).isNotNull();
        return (String) invite.get("token");
    }
}
