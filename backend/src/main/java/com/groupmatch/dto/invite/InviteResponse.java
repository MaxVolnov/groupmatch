package com.groupmatch.dto.invite;

import java.time.Instant;
import java.util.UUID;

public record InviteResponse(
        UUID id,
        UUID groupId,
        String token,
        UUID createdBy,
        Instant createdAt,
        Instant expiresAt,
        int maxUses,
        int currentUses,
        boolean revoked
) {}
