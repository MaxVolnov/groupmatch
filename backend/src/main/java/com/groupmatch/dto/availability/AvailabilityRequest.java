package com.groupmatch.dto.availability;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AvailabilityRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 200) String note
) {}
