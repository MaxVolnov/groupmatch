package com.groupmatch.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grp")
@Getter
@Setter
@NoArgsConstructor
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    /** IANA timezone для отображения дат группы. */
    @Column(name = "tz_id", nullable = false)
    private String tzId = "Europe/Moscow";

    /**
     * Когда true — участники (MEMBER) не могут создавать/изменять/удалять слоты.
     * OWNER и системный ADMIN по-прежнему могут.
     */
    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    /**
     * Когда false — теплокарта анонимна (только счётчики без имён).
     * Когда true — участники и OWNER видят имена в теплокарте.
     */
    @Column(name = "show_participants", nullable = false)
    private boolean showParticipants = false;

    /**
     * Версия для инвалидации Redis-кэша теплокарт.
     * Инкрементируется триггером БД при любом изменении availability.
     */
    @Column(nullable = false)
    private int version = 0;

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
