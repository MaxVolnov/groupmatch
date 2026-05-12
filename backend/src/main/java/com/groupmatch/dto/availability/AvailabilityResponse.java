package com.groupmatch.dto.availability;

import java.time.Instant;
import java.util.UUID;

public record AvailabilityResponse(
        UUID id,
        UUID groupId,
        UUID userId,
        Instant startsAt,
        Instant endsAt,
        String note,
        Instant createdAt
) {}
