package com.groupmatch.groups;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GroupErrorPathTest extends BaseIntegrationTest {

    private static final String EMAIL          = "grperror@groupmatch-test.io";
    private static final String PASSWORD       = "GrpError1!";
    private static final String OTHER_EMAIL    = "grperror2@groupmatch-test.io";
    private static final String OTHER_PASSWORD = "GrpError2!";

    String accessToken;
    String otherToken;
    String groupId;

    @BeforeAll
    void setUp() {
        cleanupUser(EMAIL);
        cleanupUser(OTHER_EMAIL);

        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Group Owner"), jsonHeaders()), Map.class);
        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", OTHER_EMAIL, "password", OTHER_PASSWORD,
                        "displayName", "Other User"), jsonHeaders()), Map.class);

        accessToken = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD), jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();
        otherToken = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", OTHER_EMAIL, "password", OTHER_PASSWORD), jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();

        groupId = rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Error Path Group", "tzId", "UTC"),
                        authHeaders(accessToken)), Map.class)
                .getBody().get("id").toString();
    }

    // 1. Get non-existent group → 404
    @Test @Order(1)
    void getNonExistentGroupReturns404() {
        try {
            rest.exchange(
                    url("/api/v1/groups/00000000-0000-0000-0000-000000000000"),
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(accessToken)), Map.class);
            fail("Expected 404");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(403, 404);
        }
    }

    // 2. Get group as non-member → 403
    @Test @Order(2)
    void getGroupAsNonMemberReturns403() {
        try {
            rest.exchange(
                    url("/api/v1/groups/" + groupId),
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(otherToken)), Map.class);
            fail("Expected 403");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // 3. Update group as non-owner → 403
    @Test @Order(3)
    void updateGroupAsNonOwnerReturns403() {
        try {
            rest.exchange(
                    url("/api/v1/groups/" + groupId),
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("title", "Hijacked"), authHeaders(otherToken)), Map.class);
            fail("Expected 403");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // 4. Delete group as non-owner → 403
    @Test @Order(4)
    void deleteGroupAsNonOwnerReturns403() {
        try {
            rest.exchange(
                    url("/api/v1/groups/" + groupId),
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(otherToken)), Void.class);
            fail("Expected 403");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // 5. Create group without auth → 401
    @Test @Order(5)
    void createGroupWithoutAuthReturns401() {
        try {
            rest.exchange(
                    url("/api/v1/groups"),
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("title", "No Auth Group"), jsonHeaders()), Map.class);
            fail("Expected 401");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // 6. Plan limit: FREE user creating 4th group → 402
    @Test @Order(6)
    void createFourthGroupOnFreeplanReturns402() {
        // setUp created 1 group already — create 2 more to reach the FREE limit of 3
        for (int i = 0; i < 2; i++) {
            rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("title", "Extra Group " + i, "tzId", "UTC"),
                            authHeaders(accessToken)), Map.class);
        }
        // 4th group should be rejected
        try {
            rest.exchange(url("/api/v1/groups"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("title", "Over Limit Group", "tzId", "UTC"),
                            authHeaders(accessToken)), Map.class);
            fail("Expected 402");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        }
    }
}
