package com.groupmatch.dto.admin;

import com.groupmatch.domain.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull Role role
) {}
