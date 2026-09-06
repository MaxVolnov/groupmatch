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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Передача владения группой.
 *
 * Права владельца — единственное, что нельзя вернуть себе самому, поэтому
 * проверяется не только успешный путь, но и каждый способ передать их не туда.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OwnershipTransferTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL   = "transfer-owner@groupmatch-test.io";
    private static final String MEMBER_EMAIL  = "transfer-member@groupmatch-test.io";
    private static final String OUTSIDER_EMAIL = "transfer-outsider@groupmatch-test.io";
    private static final String PASSWORD      = "TransferTest1!";

    String ownerToken;
    String memberToken;
    String ownerId;
    String memberId;
    String outsiderId;
    String groupId;

    @BeforeAll
    void setUp() {
        for (String email : List.of(OWNER_EMAIL, MEMBER_EMAIL, OUTSIDER_EMAIL)) cleanupUser(email);

        ownerId = signup(OWNER_EMAIL, "Transfer Owner");
        memberId = signup(MEMBER_EMAIL, "Transfer Member");
        outsiderId = signup(OUTSIDER_EMAIL, "Transfer Outsider");

        ownerToken = signin(OWNER_EMAIL);
        memberToken = signin(MEMBER_EMAIL);

        groupId = rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Transfer Group", "tzId", "UTC"),
                        authHeaders(ownerToken)), Map.class)
                .getBody().get("id").toString();

        rest.exchange(url("/api/v1/groups/" + groupId + "/members"), HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", memberId), authHeaders(ownerToken)), Map.class);
    }

    // ── 3. не-участнику — 400 ─────────────────────────────────────────────────
    //
    // Раньше успешного случая намеренно: после успешной передачи владельцем
    // становится другой человек, и отрицательные проверки пришлось бы делать
    // от его имени.

    @Test @Order(1)
    void transferToNonMemberIsRejected() {
        assertThat(transfer(ownerToken, outsiderId)).isEqualTo(400);
        assertThat(ownerOf(groupId)).isEqualTo(ownerId);
    }

    // ── 4. самому себе — 400 ──────────────────────────────────────────────────

    @Test @Order(2)
    void transferToSelfIsRejected() {
        assertThat(transfer(ownerToken, ownerId)).isEqualTo(400);
        assertThat(ownerOf(groupId)).isEqualTo(ownerId);
    }

    // ── 2. не-владелец не может передавать ────────────────────────────────────

    @Test @Order(3)
    void nonOwnerCannotTransfer() {
        assertThat(transfer(memberToken, memberId))
                .as("участник пытается сделать владельцем себя")
                .isEqualTo(403);
        assertThat(ownerOf(groupId))
                .as("владелец не должен смениться")
                .isEqualTo(ownerId);
    }

    // ── 1. владелец передаёт участнику ────────────────────────────────────────

    @Test @Order(4)
    void ownerTransfersToMember() {
        assertThat(transfer(ownerToken, memberId)).isEqualTo(204);

        assertThat(ownerOf(groupId))
                .as("владельцем стал участник")
                .isEqualTo(memberId);

        Map<String, String> roles = rolesInGroup(groupId);
        assertThat(roles.get(memberId)).isEqualTo("OWNER");
        assertThat(roles.get(ownerId))
                .as("прежний владелец остаётся в группе обычным участником")
                .isEqualTo("MEMBER");
        assertThat(statusesInGroup(groupId).get(ownerId)).isEqualTo("ACTIVE");
    }

    /** И обратно: права действительно перешли, а не продублировались. */
    @Test @Order(5)
    void formerOwnerLosesOwnerRights() {
        assertThat(transfer(ownerToken, ownerId))
                .as("бывший владелец пытается забрать права назад")
                .isEqualTo(403);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int transfer(String token, String newOwnerId) {
        try {
            ResponseEntity<Void> resp = rest.exchange(
                    url("/api/v1/groups/" + groupId + "/transfer-ownership"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("newOwnerId", newOwnerId), authHeaders(token)), Void.class);
            return resp.getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        } catch (Exception e) {
            return fail("Неожиданная ошибка: " + e);
        }
    }

    private String ownerOf(String groupId) {
        return jdbcTemplate.queryForObject(
                "SELECT owner_id FROM grp WHERE id = ?::uuid", String.class, groupId);
    }

    private Map<String, String> rolesInGroup(String groupId) {
        return column(groupId, "role");
    }

    private Map<String, String> statusesInGroup(String groupId) {
        return column(groupId, "status");
    }

    private Map<String, String> column(String groupId, String name) {
        return jdbcTemplate.query(
                "SELECT user_id, " + name + " AS value FROM grp_member WHERE grp_id = ?::uuid",
                rs -> {
                    Map<String, String> out = new java.util.HashMap<>();
                    while (rs.next()) out.put(rs.getString("user_id"), rs.getString("value"));
                    return out;
                }, groupId);
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
