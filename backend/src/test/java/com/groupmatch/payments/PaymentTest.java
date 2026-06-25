package com.groupmatch.payments;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PaymentTest extends BaseIntegrationTest {

    private static final String EMAIL    = "paytest@groupmatch-test.io";
    private static final String PASSWORD = "PayTest1!";

    String accessToken;

    @BeforeAll
    void setUp() {
        cleanupUser(EMAIL);

        rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD,
                        "displayName", "Pay Tester"), jsonHeaders()), Map.class);

        accessToken = rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", EMAIL, "password", PASSWORD),
                        jsonHeaders()), Map.class)
                .getBody().get("accessToken").toString();
    }

    // 1. GET /me/plan → returns FREE plan info
    @Test @Order(1)
    void getPlanInfoReturnsFree() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/me/plan"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("plan")).isEqualTo("FREE");
        assertThat(resp.getBody().get("groupLimit")).isEqualTo(3);
    }

    // 2. GET /payments/subscription → 204 (no active subscription yet)
    @Test @Order(2)
    void getSubscriptionReturnsNoContent() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/payments/subscription"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // 3. POST /payments/yookassa/create in stub mode → returns stub response
    @Test @Order(3)
    void createPaymentInStubModeSucceeds() {
        // plan is required by the backend DTO; periodMonths selects monthly vs annual
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/payments/yookassa/create"), HttpMethod.POST,
                new HttpEntity<>(Map.of("plan", "PRO", "periodMonths", 1),
                        authHeaders(accessToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("subscriptionId");
        // In stub mode confirmationUrl contains "stub"
        assertThat(resp.getBody().get("confirmationUrl").toString()).contains("stub");
    }

    // 4. POST /payments/yookassa/webhook without auth → 200 (public endpoint)
    @Test @Order(4)
    void webhookEndpointIsPublic() {
        // Unknown event — service logs a warning and returns normally
        try {
            ResponseEntity<Void> resp = rest.exchange(
                    url("/api/v1/payments/yookassa/webhook"), HttpMethod.POST,
                    new HttpEntity<>("{\"event\":\"unknown\",\"object\":{}}", jsonHeaders()),
                    Void.class);
            assertThat(resp.getStatusCode().value()).isNotIn(401, 403);
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }
    }
}
