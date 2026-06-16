package com.groupmatch.dto.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 100) String displayName,
        @Pattern(regexp = "^[A-Za-z_/+\\-]+$") String tzId
) {}
