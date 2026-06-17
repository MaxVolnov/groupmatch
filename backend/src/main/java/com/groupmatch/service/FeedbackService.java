package com.groupmatch.service;

import com.groupmatch.domain.Feedback;
import com.groupmatch.dto.feedback.FeedbackRequest;
import com.groupmatch.dto.feedback.FeedbackResponse;
import com.groupmatch.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    @Transactional
    public FeedbackResponse create(UUID userId, FeedbackRequest req) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setCategory(req.category());
        feedback.setMessage(req.message());

        Feedback saved = feedbackRepository.save(feedback);
        log.info("Feedback created. userId={}, category={}", userId, req.category());

        return new FeedbackResponse(saved.getId(), saved.getCategory(), saved.getMessage(), saved.getCreatedAt());
    }
}
