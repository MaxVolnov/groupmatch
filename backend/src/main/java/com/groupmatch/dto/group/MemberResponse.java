package com.groupmatch.dto.group;

import com.groupmatch.domain.GroupRole;
import com.groupmatch.domain.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String displayName,
        GroupRole role,
        MemberStatus status,
        Instant joinedAt
) {}
