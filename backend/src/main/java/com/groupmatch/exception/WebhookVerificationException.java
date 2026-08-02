package com.groupmatch.exception;

/** Вебхук не прошёл проверку подлинности (подпись или IP-источник). */
public class WebhookVerificationException extends RuntimeException {
    public WebhookVerificationException(String message) {
        super(message);
    }
}
