package com.groupmatch.auth;

import com.groupmatch.BaseIntegrationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AuthTest extends BaseIntegrationTest {

    private static final String EMAIL    = "authtest@groupmatch-test.io";
    private static final String PASSWORD = "AuthTest1!";
    private static final String DISPLAY  = "Auth Tester";
    private static final String NEW_PASS = "NewPass1!";

    String accessToken;
    String refreshToken;
    String resetToken;

    // ── 1. signup ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    void signupSucceeds() {
        var body = Map.of("email", EMAIL, "password", PASSWORD, "displayName", DISPLAY);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/signup"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("id");
    }

    // ── 2. duplicate email → 409 ──────────────────────────────────────────────

    @Test @Order(2)
    void signupDuplicateEmailReturns409() {
        var body = Map.of("email", EMAIL, "password", PASSWORD, "displayName", DISPLAY);
        try {
            rest.exchange(url("/api/v1/auth/signup"), HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()), Map.class);
            fail("Expected 409");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ── 3. signin ─────────────────────────────────────────────────────────────

    @Test @Order(3)
    void signinSucceeds() {
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        accessToken  = resp.getBody().get("accessToken").toString();
        refreshToken = resp.getBody().get("refreshToken").toString();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
    }

    // ── 4. wrong password → 401 ───────────────────────────────────────────────

    @Test @Order(4)
    void signinWrongPasswordReturns401() {
        var body = Map.of("email", EMAIL, "password", "WrongPassword999!");
        try {
            rest.exchange(url("/api/v1/auth/signin"), HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()), Map.class);
            fail("Expected 401");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── 5. resend verification → 200 ─────────────────────────────────────────

    @Test @Order(5)
    void resendVerificationSucceeds() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/auth/resend-verification"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(accessToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── 6. verify-email invalid token → 400 ──────────────────────────────────

    @Test @Order(6)
    void verifyEmailInvalidTokenReturns400() {
        var body = Map.of("token", "00000000-0000-0000-0000-000000000000");
        try {
            rest.exchange(url("/api/v1/auth/verify-email"), HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()), Void.class);
            fail("Expected 400");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── 7. token refresh → new access token ──────────────────────────────────

    @Test @Order(7)
    void refreshTokenSucceeds() {
        var body = Map.of("refreshToken", refreshToken);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/refresh"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newAccess = resp.getBody().get("accessToken").toString();
        assertThat(newAccess).isNotBlank();
        // update token for subsequent tests
        accessToken = newAccess;
    }

    // ── 8. forgot-password always returns 200 ────────────────────────────────

    @Test @Order(8)
    void forgotPasswordAlwaysReturns200() {
        var body = Map.of("email", "nonexistent-xyz@groupmatch-test.io");
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/auth/forgot-password"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── 9. forgot-password for real user → token saved in DB ─────────────────

    @Test @Order(9)
    void forgotPasswordSavesTokenInDb() {
        var body = Map.of("email", EMAIL);
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/auth/forgot-password"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        resetToken = jdbcTemplate.queryForObject(
                "SELECT prt.token FROM password_reset_token prt " +
                "JOIN app_user u ON u.id = prt.user_id " +
                "WHERE u.email = ? AND prt.used_at IS NULL " +
                "ORDER BY prt.expires_at DESC LIMIT 1",
                String.class, EMAIL);
        assertThat(resetToken).isNotBlank();
    }

    // ── 10. reset-password invalid token → 400 ───────────────────────────────

    @Test @Order(10)
    void resetPasswordInvalidTokenReturns400() {
        var body = Map.of("token", "00000000-0000-0000-0000-000000000000",
                          "newPassword", "SomePass1!");
        try {
            rest.exchange(url("/api/v1/auth/reset-password"), HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()), Void.class);
            fail("Expected 400");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── 11. reset-password with valid token → 200 ────────────────────────────

    @Test @Order(11)
    void resetPasswordSucceeds() {
        var body = Map.of("token", resetToken, "newPassword", NEW_PASS);
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/auth/reset-password"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── 12. signin with new password after reset ──────────────────────────────

    @Test @Order(12)
    void signinWithNewPasswordSucceeds() {
        var body = Map.of("email", EMAIL, "password", NEW_PASS);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/auth/signin"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        accessToken = resp.getBody().get("accessToken").toString();
        assertThat(accessToken).isNotBlank();
    }

    // ── 13. verify email via DB token ─────────────────────────────────────────

    @Test @Order(13)
    void verifyEmailFromDbToken() {
        String token = jdbcTemplate.queryForObject(
                "SELECT evt.token FROM email_verification_token evt " +
                "JOIN app_user u ON u.id = evt.user_id " +
                "WHERE u.email = ? AND evt.used_at IS NULL " +
                "ORDER BY evt.expires_at DESC LIMIT 1",
                String.class, EMAIL);
        assertThat(token).isNotBlank();

        var body = Map.of("token", token);
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/auth/verify-email"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Boolean verified = jdbcTemplate.queryForObject(
                "SELECT is_email_verified FROM app_user WHERE email = ?",
                Boolean.class, EMAIL);
        assertThat(verified).isTrue();
    }

    // ── 14. logout ────────────────────────────────────────────────────────────

    @Test @Order(14)
    void logoutSucceeds() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/auth/logout"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(accessToken)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
