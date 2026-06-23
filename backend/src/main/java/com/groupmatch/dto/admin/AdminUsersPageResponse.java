package com.groupmatch.dto.admin;

import java.util.List;

public record AdminUsersPageResponse(
        List<AdminUserResponse> users,
        int page,
        int totalPages,
        long totalElements
) {}
