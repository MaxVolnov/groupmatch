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

    /**
     * Удаление серии целиком. По {@code userId} тоже, а не только по
     * {@code seriesId}: идентификатор серии генерируется на каждое создание и
     * пересечься не должен, но запрос, удаляющий строки без привязки к
     * владельцу, — не то, что стоит оставлять в репозитории на будущее.
     */
    int deleteBySeriesIdAndUserId(UUID seriesId, UUID userId);
}
