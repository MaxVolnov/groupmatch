package com.groupmatch.dto.invite;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Что показать человеку, открывшему ссылку-приглашение, до того как он что-либо
 * сделает.
 *
 * Эндпоинт публичный, поэтому состав полей — вопрос безопасности, а не удобства.
 * Здесь ровно два факта: как называется группа и кто зовёт. Ни идентификатора
 * группы, ни email, ни числа участников, ни дат — всё это утекало бы любому, кто
 * подобрал токен, и ни одно из них человеку на этом экране не нужно.
 *
 * {@code null}-поля из ответа выпадают: у валидного приглашения нет причины
 * отказа, у невалидного — названия группы.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitePreviewResponse(
        boolean valid,
        String groupName,
        String inviterName,
        String reason
) {

    /** Причина, по которой приглашение не сработает. */
    public enum Reason {
        NOT_FOUND("not_found"),
        EXPIRED("expired"),
        REVOKED("revoked"),
        MAX_USES("max_uses");

        private final String wireValue;

        Reason(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public static InvitePreviewResponse valid(String groupName, String inviterName) {
        return new InvitePreviewResponse(true, groupName, inviterName, null);
    }

    public static InvitePreviewResponse invalid(Reason reason) {
        return new InvitePreviewResponse(false, null, null, reason.wireValue());
    }
}
