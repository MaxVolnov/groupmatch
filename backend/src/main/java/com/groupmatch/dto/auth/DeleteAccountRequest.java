package com.groupmatch.dto.auth;

/**
 * Тело {@code DELETE /api/v1/me}.
 *
 * Пароль без {@code @NotBlank}: у гостевого аккаунта его нет и никогда не было,
 * а отдельный эндпоинт под гостя означал бы два пути к одному и тому же
 * необратимому действию. Обязательность проверяется в сервисе, где известно,
 * гость перед нами или нет.
 */
public record DeleteAccountRequest(String password) {}
