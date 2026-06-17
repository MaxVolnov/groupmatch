package com.groupmatch.dto.feedback;

import com.groupmatch.domain.FeedbackCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotNull FeedbackCategory category,
        @NotBlank @Size(min = 10, max = 2000) String message
) {}
