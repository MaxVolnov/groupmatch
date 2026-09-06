package com.groupmatch.dto.group;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Тело {@code POST /api/v1/groups/{groupId}/transfer-ownership}. */
public record TransferOwnershipRequest(
        @NotNull(message = "newOwnerId is required")
        UUID newOwnerId
) {}
