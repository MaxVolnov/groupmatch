package com.groupmatch.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminGroupResponse(
        UUID id,
        String title,
        String description,
        String timezone,
        UUID ownerId,
        String ownerEmail,
        String ownerDisplayName,
        int memberCount,
        boolean isLocked,
        Instant createdAt
) {}
