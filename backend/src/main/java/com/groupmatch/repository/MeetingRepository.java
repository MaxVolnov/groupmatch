package com.groupmatch.repository;

import com.groupmatch.domain.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    List<Meeting> findByGroupIdOrderByStartsAtDesc(UUID groupId);
}
