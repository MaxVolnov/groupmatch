package com.groupmatch.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestRequest(
        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 50, message = "Display name must be between 2 and 50 characters")
        String displayName
) {}
