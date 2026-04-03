package com.groupmatch.dto.meeting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record MeetingRequest(
        @NotBlank @Size(min = 3, max = 100) String title,
        @Size(max = 2000) String description,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {}
