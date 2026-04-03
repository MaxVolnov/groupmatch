package com.groupmatch.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupRequest(
        @NotBlank @Size(min = 3, max = 100) String title,
        @Size(max = 1000) String description,
        String tzId,
        Boolean locked,
        Boolean showParticipants
) {}
