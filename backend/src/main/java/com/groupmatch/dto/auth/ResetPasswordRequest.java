package com.groupmatch.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ResetPasswordRequest(
    @NotNull UUID token,
    @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
