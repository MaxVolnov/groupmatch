package com.groupmatch.domain;

/**
 * Статус участника в группе.
 * ACTIVE  — действующий участник.
 * LEFT    — вышел добровольно; слоты остаются для истории, но редактирование закрыто.
 * BANNED  — забанен владельцем; слоты удаляются, доступ запрещён.
 */
public enum MemberStatus {
    ACTIVE,
    LEFT,
    BANNED
}
