package com.groupmatch.util;

/**
 * Приведение токена к виду, пригодному для лога.
 *
 * Одна и та же реализация жила в {@code AuthService} и
 * {@code RefreshTokenService}. Копии совпадали, но у этой функции есть цена
 * ошибки: она решает, сколько символов секрета попадёт в лог. Такое лучше
 * держать в одном месте — чтобы «увеличу префикс, так удобнее отлаживать»
 * обсуждалось один раз, а не расходилось между файлами молча.
 */
public final class TokenMasker {

    /** Короткие строки не маскируем частично: восемь символов от токена длиной
     *  меньше восьми — это весь токен. */
    private static final int VISIBLE_PREFIX = 8;

    private TokenMasker() {}

    public static String mask(String token) {
        if (token == null || token.length() < VISIBLE_PREFIX) return "***";
        return token.substring(0, VISIBLE_PREFIX) + "***";
    }
}
