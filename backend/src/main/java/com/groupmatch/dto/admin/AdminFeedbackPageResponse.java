package com.groupmatch.dto.admin;

import java.util.List;

public record AdminFeedbackPageResponse(
        List<AdminFeedbackResponse> items,
        int page,
        int totalPages,
        long totalElements
) {}
