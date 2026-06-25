package com.groupmatch.dto.plan;

public record PlanInfoResponse(
        String plan,
        int ownedGroups,
        int groupLimit  // -1 means unlimited
) {}
