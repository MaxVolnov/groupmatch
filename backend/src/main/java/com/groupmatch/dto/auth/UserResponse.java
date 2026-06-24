package com.groupmatch.dto.auth;

import com.groupmatch.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String tzId,
        String plan,
        String role,
        boolean isEmailVerified,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTzId(),
                user.getPlan().name(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
