package com.groupmatch.groups;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GroupTest extends BaseIntegrationTest {

    private static final String EMAIL    = "grouptest@groupmatch-test.io";
    private static final String PASSWORD = "GroupTest1!";

    String accessToken;
    String groupId;

    @BeforeAll
    void setUp() {
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Group Tester"), jsonHeaders()), Map.class);

        ResponseEntity<Map> signinResp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                        jsonHeaders()), Map.class);
        accessToken = signinResp.getBody().get("accessToken").toString();
    }

    // ── 1. create group → 201 ─────────────────────────────────────────────────

    @Test @Order(1)
    void createGroupSucceeds() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "GT Group", "tzId", "Europe/London"),
                        authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("id");
        groupId = resp.getBody().get("id").toString();
    }

    // ── 2. get group → 200 ────────────────────────────────────────────────────

    @Test @Order(2)
    void getGroupSucceeds() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("id").toString()).isEqualTo(groupId);
        assertThat(resp.getBody().get("title")).isEqualTo("GT Group");
    }

    // ── 3. update group → 200 ─────────────────────────────────────────────────

    @Test @Order(3)
    void updateGroupSucceeds() {
        var body = Map.of("title", "GT Group Updated", "tzId", "Europe/Paris",
                          "locked", false, "showParticipants", true);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/groups/" + groupId), HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("title")).isEqualTo("GT Group Updated");
        assertThat(resp.getBody().get("tzId")).isEqualTo("Europe/Paris");
    }

    // ── 4. list groups → contains created ────────────────────────────────────

    @Test @Order(4)
    @SuppressWarnings("unchecked")
    void listGroupsContainsCreated() {
        ResponseEntity<List> resp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();
        assertThat(resp.getBody())
                .anyMatch(item -> groupId.equals(((Map<?, ?>) item).get("id").toString()));
    }

    // ── 5. delete group → 204 ────────────────────────────────────────────────

    @Test @Order(5)
    void deleteGroupSucceeds() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/groups/" + groupId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(accessToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
