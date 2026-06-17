package com.groupmatch.dto.feedback;

import com.groupmatch.domain.FeedbackCategory;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        FeedbackCategory category,
        String message,
        Instant createdAt
) {}
