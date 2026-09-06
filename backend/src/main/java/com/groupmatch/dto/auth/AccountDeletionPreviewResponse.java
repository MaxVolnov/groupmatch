package com.groupmatch.dto.auth;

import java.util.List;

/**
 * Ответ {@code GET /api/v1/me/deletion-preview} — материал для подтверждения.
 *
 * Считается на сервере: у клиента нет состава участников чужих групп, а без
 * него нельзя сказать, перейдёт группа или исчезнет.
 *
 * @param graceDays срок отсрочки; интерфейс называет его в подтверждении, и
 *                  брать его из конфига надёжнее, чем повторять число в текстах
 */
public record AccountDeletionPreviewResponse(
        List<OwnedGroupFate> ownedGroups,
        int graceDays
) {}
