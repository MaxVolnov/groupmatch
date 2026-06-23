package com.groupmatch.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record BanUserRequest(
        @NotBlank(message = "Ban reason is required")
        String reason
) {}
