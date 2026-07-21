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

    /** Письма отправляются на языке пользователя: locale == "ru" → русский, иначе английский. */
    private boolean isRu(String locale) {
        return locale == null || "ru".equalsIgnoreCase(locale);
    }

    public void sendVerificationEmail(String to, String locale, UUID token) {
        boolean isRu = isRu(locale);
        String link = baseUrl + "/verify-email?token=" + token;
        String subject = isRu ? "Подтвердите email — GroupMatch" : "Verify your email — GroupMatch";
        String body = isRu
                ? "Подтвердите ваш email адрес, нажав на кнопку ниже. Ссылка действительна 24 часа."
                : "Please verify your email address by clicking the button below. The link is valid for 24 hours.";
        String buttonText = isRu ? "Подтвердить email" : "Verify email";
        send(to, subject, buildEmail(body, buttonText, link));
    }

    public void sendPasswordResetEmail(String to, String locale, UUID token) {
        boolean isRu = isRu(locale);
        String link = baseUrl + "/reset-password?token=" + token;
        String subject = isRu ? "Сброс пароля — GroupMatch" : "Reset your password — GroupMatch";
        String body = isRu
                ? "Вы запросили сброс пароля. Нажмите кнопку ниже. Ссылка действительна 1 час."
                : "You requested a password reset. Click the button below. The link is valid for 1 hour.";
        String buttonText = isRu ? "Сбросить пароль" : "Reset password";
        send(to, subject, buildEmail(body, buttonText, link));
    }

    public void sendMemberJoinedEmail(String to, String locale, String joinerName, String groupTitle) {
        boolean isRu = isRu(locale);
        String subject = (isRu ? "Новый участник — " : "New member — ") + groupTitle;
        String body = isRu
                ? joinerName + " присоединился к группе «" + groupTitle + "»"
                : joinerName + " joined the group \"" + groupTitle + "\"";
        send(to, subject, buildEmail(body, null, null));
    }

    /** Приглашение в группу по email. Пока не вызывается ни из одного места — InviteService
     *  делится только ссылкой-приглашением, письма не шлёт; метод готов для будущей фичи. */
    public void sendGroupInviteEmail(String to, String locale, String groupTitle, String joinUrl) {
        boolean isRu = isRu(locale);
        String subject = isRu ? "Вас пригласили в группу — GroupMatch" : "You've been invited — GroupMatch";
        String body = isRu
                ? "Вас пригласили в группу «" + groupTitle + "». Нажмите кнопку чтобы присоединиться."
                : "You've been invited to join \"" + groupTitle + "\". Click the button to join.";
        String buttonText = isRu ? "Присоединиться" : "Join group";
        send(to, subject, buildEmail(body, buttonText, joinUrl));
    }

    public void sendMeetingReminderEmail(String to, String locale, String meetingTitle,
                                         String groupTitle, Instant startsAt, String tzId) {
        boolean isRu = isRu(locale);
        ZonedDateTime zdt = startsAt.atZone(ZoneId.of(tzId));
        String formatted = zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm z"));
        String subject = isRu
                ? "Напоминание: «" + meetingTitle + "» начинается через час"
                : "Reminder: \"" + meetingTitle + "\" starts in 1 hour";
        String body = isRu
                ? "Встреча «" + meetingTitle + "» в группе «" + groupTitle + "» начинается в " + formatted + "."
                : "The meeting \"" + meetingTitle + "\" in group \"" + groupTitle + "\" starts at " + formatted + ".";
        send(to, subject, buildEmail(body, null, null));
    }

    private String buildEmail(String body, String buttonText, String buttonUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
              <h2 style="color: #2563eb;">GroupMatch</h2>
              <p>%s</p>
              %s
              <p style="color: #6b7280; font-size: 12px; margin-top: 32px;">GroupMatch · groupmatch.app</p>
            </body>
            </html>
            """.formatted(
                body,
                buttonText != null && buttonUrl != null
                    ? "<a href=\"%s\" style=\"display:inline-block;background:#2563eb;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600;\">%s</a>".formatted(buttonUrl, buttonText)
                    : ""
            );
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
