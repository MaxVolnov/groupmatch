package com.groupmatch.dto.admin;

import com.groupmatch.domain.Plan;
import jakarta.validation.constraints.NotNull;

public record ChangePlanRequest(
        @NotNull Plan plan
) {}
