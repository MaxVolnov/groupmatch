package com.groupmatch.dto.group;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String title,
        String description,
        String tzId,
        boolean locked,
        boolean showParticipants,
        UUID ownerId,
        int version,
        Instant createdAt,
        Instant updatedAt
) {}
