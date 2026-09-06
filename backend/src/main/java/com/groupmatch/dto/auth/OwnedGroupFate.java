package com.groupmatch.dto.auth;

import java.util.UUID;

/**
 * Что станет с одной группой, если её владелец удалит аккаунт.
 *
 * @param willBeDeleted группа исчезнет — других активных участников нет
 * @param newOwnerName  кому перейдёт владение; {@code null}, когда группа удаляется
 */
public record OwnedGroupFate(
        UUID groupId,
        String title,
        boolean willBeDeleted,
        String newOwnerName
) {}
