package com.groupmatch.repository;

import com.groupmatch.domain.GrpMember;
import com.groupmatch.domain.GrpMemberId;
import com.groupmatch.domain.GroupRole;
import com.groupmatch.domain.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GrpMemberRepository extends JpaRepository<GrpMember, GrpMemberId> {

    Optional<GrpMember> findByGroupAndUser(UUID groupId, UUID userId);

    boolean existsByGroupAndUserAndStatus(UUID groupId, UUID userId, MemberStatus status);

    long countByGroupAndStatus(UUID groupId, MemberStatus status);

    boolean existsByGroupAndUserAndRole(UUID groupId, UUID userId, GroupRole role);

    List<GrpMember> findByGroupAndStatus(UUID groupId, MemberStatus status);

    List<GrpMember> findByUserAndStatus(UUID userId, MemberStatus status);
}
