package com.groupmatch.repository;

import com.groupmatch.domain.Feedback;
import com.groupmatch.domain.FeedbackCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Page<Feedback> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<Feedback> findByCategory(FeedbackCategory category, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<Feedback> findByResolved(boolean resolved, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<Feedback> findByCategoryAndResolved(FeedbackCategory category, boolean resolved, Pageable pageable);
}
