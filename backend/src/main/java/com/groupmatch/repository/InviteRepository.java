package com.groupmatch.repository;

import com.groupmatch.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByToken(String token);

    List<Invite> findByGroupIdAndRevokedFalse(UUID groupId);

    /** Count invites created by a user for a group after a given time (rate-limit check). */
    long countByGroupIdAndCreatedByAndCreatedAtAfter(UUID groupId, UUID createdBy, Instant since);
}
