package com.groupmatch.repository;

import com.groupmatch.domain.Availability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findByGroupIdAndUserId(UUID groupId, UUID userId);

    long countByGroupIdAndUserId(UUID groupId, UUID userId);

    /** All slots for a group that overlap [from, to] — used for heatmap. */
    List<Availability> findByGroupIdAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID groupId, Instant before, Instant after);

    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);
}
