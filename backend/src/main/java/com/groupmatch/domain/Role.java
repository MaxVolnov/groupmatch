package com.groupmatch.domain;

/**
 * Системная роль пользователя (для Spring Security и JWT).
 * USER  — обычный пользователь платформы.
 * ADMIN — модератор/администратор; может читать/изменять любые группы
 *         и обрабатывать репорты через /api/v1/admin/**.
 *
 * Не путать с GroupRole (OWNER/MEMBER) — это роль внутри конкретной группы.
 */
public enum Role {
    USER,
    ADMIN;

    /** Префикс, которого ожидает Spring Security в GrantedAuthority. */
    public String toAuthority() {
        return "ROLE_" + this.name();
    }
}
