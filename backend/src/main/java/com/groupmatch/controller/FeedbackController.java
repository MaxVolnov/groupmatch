package com.groupmatch.controller;

import com.groupmatch.dto.feedback.FeedbackRequest;
import com.groupmatch.dto.feedback.FeedbackResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                   @Valid @RequestBody FeedbackRequest req) {
        return feedbackService.create(principal.getId(), req);
    }
}
