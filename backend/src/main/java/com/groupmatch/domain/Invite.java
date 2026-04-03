package com.groupmatch.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invite")
@Getter
@Setter
@NoArgsConstructor
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "grp_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** 0 = unlimited uses. */
    @Column(name = "max_uses", nullable = false)
    private int maxUses = 0;

    @Column(name = "current_uses", nullable = false)
    private int currentUses = 0;

    @Column(name = "is_revoked", nullable = false)
    private boolean revoked = false;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isValid() {
        return !revoked
                && Instant.now().isBefore(expiresAt)
                && (maxUses == 0 || currentUses < maxUses);
    }
}
