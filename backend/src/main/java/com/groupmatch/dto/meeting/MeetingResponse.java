package com.groupmatch.dto.meeting;

import java.time.Instant;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        UUID groupId,
        UUID creatorId,
        String title,
        String description,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt
) {}
