package com.groupmatch.util;

/**
 * Приведение адреса почты к виду, пригодному для лога.
 *
 * Зачем. Логи прода — это персональные данные ровно в той мере, в какой в них
 * попадает адрес: по строке {@code Signup attempt for email: ivan@example.com}
 * видно, что конкретный человек пользуется сервисом. Продукт работает по
 * 152-ФЗ, и держать такое в логах платформы бессрочно — отдельный риск, никак
 * не связанный с тем, ради чего эта строка писалась.
 *
 * При этом совсем убирать адрес нельзя: без него нельзя ответить на вопрос
 * «почему у этого человека не проходит регистрация», ради которого логи и
 * читают. Домен остаётся: он отвечает на «а не корпоративная ли это почта с
 * жёстким спам-фильтром», а человека сам по себе не называет.
 *
 * По той же причине, что и {@link TokenMasker}, это отдельный класс: решение
 * «сколько символов адреса попадает в лог» лучше принимать один раз, чем
 * разойтись по вызовам, когда кому-то станет неудобно отлаживать.
 */
public final class EmailMasker {

    private static final String FULLY_MASKED = "***";

    private EmailMasker() {}

    /**
     * {@code ivan@example.com} → {@code i***@example.com}.
     *
     * Локальная часть длиной в один символ не маскируется частично, а
     * скрывается целиком: первый символ такого адреса — это и есть весь адрес.
     */
    public static String mask(String email) {
        if (email == null || email.isBlank()) return FULLY_MASKED;

        int at = email.lastIndexOf('@');
        // Без «собаки» это не адрес, а неизвестно что: показывать нечего.
        if (at <= 0 || at == email.length() - 1) return FULLY_MASKED;

        String local = email.substring(0, at);
        String domain = email.substring(at); // вместе с '@'

        if (local.length() <= 1) return FULLY_MASKED + domain;
        return local.charAt(0) + FULLY_MASKED + domain;
    }
}
