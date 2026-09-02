package com.groupmatch.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "availability")
@Getter
@Setter
@NoArgsConstructor
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "grp_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /** Optional note, max 200 chars (e.g. "Preferred", "Maybe"). */
    @Column(length = 200)
    private String note;

    /**
     * Общий идентификатор слотов одной повторяющейся серии; {@code null} у
     * одиночного слота.
     *
     * Серия хранится развёрнутой в обычные слоты, правила повторения в базе
     * нет — это поле только связывает строки, заведённые одним действием,
     * чтобы их можно было так же одним действием удалить.
     */
    @Column(name = "series_id")
    private UUID seriesId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Availability(UUID groupId, UUID userId, Instant startsAt, Instant endsAt, String note) {
        this(groupId, userId, startsAt, endsAt, note, null);
    }

    public Availability(UUID groupId, UUID userId, Instant startsAt, Instant endsAt, String note,
                        UUID seriesId) {
        this.groupId = groupId;
        this.userId = userId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.note = note;
        this.seriesId = seriesId;
    }
}
