package com.groupmatch.dto.admin;

import java.util.List;

public record AdminGroupPageResponse(
        List<AdminGroupResponse> groups,
        int page,
        int totalPages,
        long totalElements
) {}
