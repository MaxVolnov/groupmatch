package com.groupmatch.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Участник группы.
 *
 * Роли внутри группы (GroupRole):
 *   OWNER  — создатель/владелец; полные права: редактирование группы,
 *            управление участниками, создание встреч, инвайты.
 *   MEMBER — обычный участник; может добавлять/редактировать только свои слоты.
 *
 * Статус (MemberStatus):
 *   ACTIVE — активный участник.
 *   LEFT   — вышел добровольно (DELETE /members/{userId} от самого участника).
 *            Слоты остаются в БД для истории теплокарты.
 *   BANNED — заблокирован владельцем (DELETE /members/{userId} от OWNER).
 *            Слоты удаляются; повторный join через инвайт запрещён.
 */
@Entity
@Table(name = "grp_member")
@IdClass(GrpMemberId.class)
@Getter
@Setter
@NoArgsConstructor
public class GrpMember {

    @Id
    @Column(name = "grp_id", nullable = false)
    private UUID group;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID user;

    /**
     * Роль внутри группы.
     * Хранится в БД как строка (OWNER/MEMBER).
     * Ровно один OWNER на группу — обеспечивается partial unique index в БД.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupRole role = GroupRole.MEMBER;

    /**
     * Статус участника.
     * Хранится в БД как строка (ACTIVE/LEFT/BANNED).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
    }

    public GrpMember(UUID groupId, UUID userId, GroupRole role, MemberStatus status) {
        this.group = groupId;
        this.user = userId;
        this.role = role;
        this.status = status;
    }

    public boolean isActive() {
        return MemberStatus.ACTIVE == status;
    }

    public boolean isOwner() {
        return GroupRole.OWNER == role;
    }
}
