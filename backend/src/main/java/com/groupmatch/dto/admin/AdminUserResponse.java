package com.groupmatch.dto.admin;

import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Role;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        Role role,
        Plan plan,
        boolean isGuest,
        boolean isBanned,
        Instant createdAt
) {}
