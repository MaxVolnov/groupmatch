package com.groupmatch.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminFeedbackResponse(
        UUID id,
        String category,
        String message,
        String authorEmail,
        String authorDisplayName,
        boolean resolved,
        Instant resolvedAt,
        Instant createdAt
) {}
