package com.groupmatch.dto.invite;

import jakarta.validation.constraints.Min;

import java.time.Instant;

public record CreateInviteRequest(
        /** null → default 30 days from now */
        Instant expiresAt,
        /** 0 = unlimited */
        @Min(0) int maxUses
) {
    public CreateInviteRequest {
        if (maxUses < 0) maxUses = 0;
    }
}
