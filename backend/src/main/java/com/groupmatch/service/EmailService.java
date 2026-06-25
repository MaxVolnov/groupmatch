package com.groupmatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class EmailService {

    private final RestClient restClient;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.base-url}")
    private String baseUrl;

    @Value("${spring.mail.password:}")
    private String resendApiKey;

    public EmailService(ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    public void sendVerificationEmail(String to, String displayName, UUID token) {
        String link = baseUrl + "/verify-email?token=" + token;
        String subject = "Confirm your GroupMatch email";
        String html = """
            <p>Hi %s,</p>
            <p>Click the button below to confirm your email address:</p>
            <p><a href="%s" style="background:#4f46e5;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none;display:inline-block;">Confirm email</a></p>
            <p>Link expires in 24 hours.</p>
            <p>If you didn't create a GroupMatch account, ignore this email.</p>
            """.formatted(displayName, link);
        send(to, subject, html);
    }

    public void sendPasswordResetEmail(String to, String displayName, UUID token) {
        String link = baseUrl + "/reset-password?token=" + token;
        String subject = "Reset your GroupMatch password";
        String html = """
            <p>Hi %s,</p>
            <p>Click the button below to reset your password:</p>
            <p><a href="%s" style="background:#4f46e5;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none;display:inline-block;">Reset password</a></p>
            <p>Link expires in 1 hour.</p>
            <p>If you didn't request a password reset, ignore this email.</p>
            """.formatted(displayName, link);
        send(to, subject, html);
    }

    public void sendMemberJoinedEmail(String to, String ownerName, String joinerName, String groupTitle) {
        String subject = joinerName + " joined your group on GroupMatch";
        String html = """
            <p>Hi %s,</p>
            <p><strong>%s</strong> has joined your group <strong>%s</strong>.</p>
            <p>Head over to GroupMatch to see the updated availability heatmap.</p>
            """.formatted(ownerName, joinerName, groupTitle);
        send(to, subject, html);
    }

    public void sendMeetingReminderEmail(String to, String displayName, String meetingTitle,
                                         String groupTitle, Instant startsAt, String tzId) {
        ZonedDateTime zdt = startsAt.atZone(ZoneId.of(tzId));
        String formatted = zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm z"));
        String subject = "Reminder: \"" + meetingTitle + "\" starts in 1 hour";
        String html = """
            <p>Hi %s,</p>
            <p>This is a reminder that the meeting <strong>%s</strong> in group <strong>%s</strong> starts at <strong>%s</strong>.</p>
            """.formatted(displayName, meetingTitle, groupTitle, formatted);
        send(to, subject, html);
    }

    private void send(String to, String subject, String htmlBody) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY not configured, skipping email. to={}, subject={}", to, subject);
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                "from", from,
                "to", new String[]{to},
                "subject", subject,
                "html", htmlBody
            );

            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email sent via Resend API. to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email via Resend API. to={}, subject={}, error={}", to, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
