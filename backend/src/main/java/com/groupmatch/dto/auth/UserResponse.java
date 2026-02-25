package com.groupmatch.dto.auth;

import com.groupmatch.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String tzid,
        String plan,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTzid(),
                user.getPlan(),
                user.getCreatedAt()
        );
    }
}