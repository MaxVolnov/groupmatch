package com.groupmatch.repository;

import com.groupmatch.domain.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    List<Meeting> findByGroupIdOrderByStartsAtDesc(UUID groupId);

    @Query("SELECT m FROM Meeting m WHERE m.startsAt >= :from AND m.startsAt < :to AND m.reminderSent = false")
    List<Meeting> findUpcomingForReminder(@Param("from") Instant from, @Param("to") Instant to);
}
