package com.groupmatch.notifications;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NotificationTest extends BaseIntegrationTest {

    private static final String OWNER_EMAIL    = "notif-owner@groupmatch-test.io";
    private static final String OWNER_PASSWORD = "Owner1!";
    private static final String MEMBER_EMAIL    = "notif-member@groupmatch-test.io";
    private static final String MEMBER_PASSWORD = "Member1!";

    String ownerToken;
    String memberToken;
    String groupId;
    String notificationId;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void setUp() {
        // Owner
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", OWNER_PASSWORD,
                        "displayName", "Notif Owner"), jsonHeaders()), Map.class);
        ResponseEntity<Map> ownerSignin = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", OWNER_EMAIL, "password", OWNER_PASSWORD),
                        jsonHeaders()), Map.class);
        ownerToken = ownerSignin.getBody().get("accessToken").toString();

        // Group
        ResponseEntity<Map> groupResp = rest.exchange(
                url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Notif Group", "tzId", "UTC"),
                        authHeaders(ownerToken)), Map.class);
        groupId = groupResp.getBody().get("id").toString();

        // Invite
        ResponseEntity<Map> inviteResp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/invites"), HttpMethod.POST,
                new HttpEntity<>(Map.of("maxUses", 0), authHeaders(ownerToken)), Map.class);
        String inviteToken = inviteResp.getBody().get("token").toString();

        // Member signup + signin
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", MEMBER_EMAIL, "password", MEMBER_PASSWORD,
                        "displayName", "Notif Member"), jsonHeaders()), Map.class);
        ResponseEntity<Map> memberSignin = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", MEMBER_EMAIL, "password", MEMBER_PASSWORD),
                        jsonHeaders()), Map.class);
        memberToken = memberSignin.getBody().get("accessToken").toString();

        // Member joins → triggers MEMBER_JOINED notification for owner
        rest.exchange(url("/api/v1/invites/" + inviteToken + "/join"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(memberToken)), Map.class);
    }

    // ── 1. owner has MEMBER_JOINED notification ───────────────────────────────

    @Test @Order(1)
    @SuppressWarnings("unchecked")
    void notificationsListContainsMemberJoined() {
        ResponseEntity<List> resp = rest.exchange(
                url("/api/v1/notifications"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> notifications = (List<Map<?, ?>>) resp.getBody();
        assertThat(notifications).isNotEmpty();
        Map<?, ?> first = notifications.get(0);
        assertThat(first.get("type")).isEqualTo("MEMBER_JOINED");
        assertThat((Boolean) first.get("read")).isFalse();
        notificationId = first.get("id").toString();
    }

    // ── 2. unread count ≥ 1 ──────────────────────────────────────────────────

    @Test @Order(2)
    void unreadCountIsPositive() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/notifications/unread-count"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) resp.getBody().get("count")).longValue()).isGreaterThanOrEqualTo(1L);
    }

    // ── 3. mark single notification read → 204 ───────────────────────────────

    @Test @Order(3)
    void markSingleNotificationRead() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/notifications/" + notificationId + "/read"), HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(ownerToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ── 4. unread count drops to 0 ───────────────────────────────────────────

    @Test @Order(4)
    void unreadCountDropsAfterMarkRead() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/notifications/unread-count"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) resp.getBody().get("count")).longValue()).isEqualTo(0L);
    }

    // ── 5. mark-all-read → 204 ───────────────────────────────────────────────

    @Test @Order(5)
    void markAllNotificationsRead() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/notifications/read-all"), HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(ownerToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ── 6. create meeting → member gets MEETING_CREATED notification ──────────

    @Test @Order(6)
    @SuppressWarnings("unchecked")
    void createMeetingNotifiesMember() {
        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        var body = Map.of(
                "title", "Notif Meeting",
                "startsAt", startsAt.toString(),
                "endsAt", startsAt.plus(1, ChronoUnit.HOURS).toString()
        );
        ResponseEntity<Map> createResp = rest.exchange(
                url("/api/v1/groups/" + groupId + "/meetings"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(ownerToken)), Map.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<List> notifResp = rest.exchange(
                url("/api/v1/notifications"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(memberToken)), List.class);

        assertThat(notifResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> memberNotifs = (List<Map<?, ?>>) notifResp.getBody();
        assertThat(memberNotifs).anyMatch(n -> "MEETING_CREATED".equals(n.get("type")));
    }
}
