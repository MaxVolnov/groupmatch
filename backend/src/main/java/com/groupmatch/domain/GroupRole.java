package com.groupmatch.domain;

/**
 * Роль участника внутри группы.
 * Хранится в БД как строка (OWNER/MEMBER).
 */
public enum GroupRole {
    OWNER,
    MEMBER
}
