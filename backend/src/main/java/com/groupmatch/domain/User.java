package com.groupmatch.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "tz_id", nullable = false)
    private String tzId = "Europe/Moscow";

    /**
     * Тарифный план. Хранится в БД как строка (FREE/PRO/TEAM).
     * Дублируется в JWT payload для быстрой проверки лимитов без обращения к БД.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Plan plan = Plan.FREE;

    /**
     * Системная роль (USER / ADMIN).
     * Хранится в БД как строка; кладётся в JWT для авторизации.
     * По умолчанию USER; ADMIN выдаётся вручную через миграцию или admin-скрипт.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.USER;

    @Column(name = "is_guest", nullable = false)
    private boolean guest = false;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
