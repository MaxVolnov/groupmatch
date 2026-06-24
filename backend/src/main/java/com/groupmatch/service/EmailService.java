package com.groupmatch.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
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

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent. to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email. to={}, subject={}, error={}", to, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
