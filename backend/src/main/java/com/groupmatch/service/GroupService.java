package com.groupmatch.service;

import com.groupmatch.domain.*;
import com.groupmatch.dto.group.AddMemberRequest;
import com.groupmatch.dto.group.GroupRequest;
import com.groupmatch.dto.group.GroupResponse;
import com.groupmatch.dto.group.MemberResponse;
import com.groupmatch.exception.*;
import com.groupmatch.repository.AvailabilityRepository;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GrpMemberRepository grpMemberRepository;
    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;

    @Transactional
    public GroupResponse createGroup(UUID callerId, Plan callerPlan, GroupRequest req) {
        long owned = groupRepository.countByOwnerId(callerId);
        int maxGroups = callerPlan.limits().maxGroups();
        if (owned >= maxGroups) {
            throw new PlanLimitExceededException(
                    "Plan limit reached: max " + maxGroups + " groups for " + callerPlan + " plan");
        }

        User owner = userRepository.findById(callerId)
                .orElseThrow(() -> new UserNotFoundException(callerId));

        Group group = new Group();
        group.setOwner(owner);
        group.setTitle(req.title());
        group.setDescription(req.description());
        if (req.tzId() != null) group.setTzId(req.tzId());
        if (req.locked() != null) group.setLocked(req.locked());
        if (req.showParticipants() != null) group.setShowParticipants(req.showParticipants());

        group = groupRepository.save(group);

        // DB trigger trg_grp_add_owner inserts owner into grp_member automatically
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listGroups(UUID callerId) {
        List<UUID> groupIds = grpMemberRepository
                .findByUserAndStatus(callerId, MemberStatus.ACTIVE)
                .stream()
                .map(GrpMember::getGroup)
                .toList();

        return groupRepository.findAllById(groupIds)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID groupId, UUID callerId) {
        requireActiveMember(groupId, callerId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        return toResponse(group);
    }

    @Transactional
    public GroupResponse updateGroup(UUID groupId, UUID callerId, GroupRequest req) {
        requireOwner(groupId, callerId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        group.setTitle(req.title());
        group.setDescription(req.description());
        if (req.tzId() != null) group.setTzId(req.tzId());
        if (req.locked() != null) group.setLocked(req.locked());
        if (req.showParticipants() != null) group.setShowParticipants(req.showParticipants());

        return toResponse(groupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(UUID groupId, UUID callerId) {
        requireOwner(groupId, callerId);
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        groupRepository.deleteById(groupId);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(UUID groupId, UUID callerId) {
        requireActiveMember(groupId, callerId);

        List<GrpMember> members = grpMemberRepository
                .findByGroupAndStatus(groupId, MemberStatus.ACTIVE);

        List<UUID> userIds = members.stream().map(GrpMember::getUser).toList();
        Map<UUID, User> users = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return members.stream()
                .map(m -> {
                    User u = users.get(m.getUser());
                    String name = u != null ? u.getDisplayName() : m.getUser().toString();
                    return new MemberResponse(m.getUser(), name, m.getRole(), m.getStatus(), m.getJoinedAt());
                })
                .toList();
    }

    @Transactional
    public MemberResponse addMember(UUID groupId, UUID callerId, Plan callerPlan, AddMemberRequest req) {
        requireOwner(groupId, callerId);

        UUID targetId = req.userId();
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        grpMemberRepository.findByGroupAndUser(groupId, targetId).ifPresent(m -> {
            if (m.getStatus() == MemberStatus.BANNED) throw new MemberBannedException();
            if (m.getStatus() == MemberStatus.ACTIVE) throw new MemberAlreadyExistsException();
        });

        long current = grpMemberRepository.countByGroupAndStatus(groupId, MemberStatus.ACTIVE);
        int maxMembers = callerPlan.limits().maxMembersPerGroup();
        if (current >= maxMembers) {
            throw new PlanLimitExceededException(
                    "Plan limit reached: max " + maxMembers + " members per group for " + callerPlan + " plan");
        }

        GrpMember member = grpMemberRepository.findByGroupAndUser(groupId, targetId)
                .orElse(new GrpMember(groupId, targetId, GroupRole.MEMBER, MemberStatus.ACTIVE));
        member.setStatus(MemberStatus.ACTIVE);
        member = grpMemberRepository.save(member);

        return new MemberResponse(member.getUser(), target.getDisplayName(),
                member.getRole(), member.getStatus(), member.getJoinedAt());
    }

    @Transactional
    public void removeMember(UUID groupId, UUID callerId, UUID targetId) {
        GrpMember target = grpMemberRepository.findByGroupAndUser(groupId, targetId)
                .filter(GrpMember::isActive)
                .orElseThrow(NotGroupMemberException::new);

        if (target.isOwner()) {
            throw new NotGroupOwnerException(); // can't remove the owner
        }

        if (callerId.equals(targetId)) {
            // voluntary leave — slots are kept for heatmap history
            target.setStatus(MemberStatus.LEFT);
        } else {
            requireOwner(groupId, callerId);
            // ban by owner — delete the member's slots per spec
            target.setStatus(MemberStatus.BANNED);
            availabilityRepository.deleteByGroupIdAndUserId(groupId, targetId);
        }
        grpMemberRepository.save(target);
    }

    // --- helpers ---

    private void requireActiveMember(UUID groupId, UUID callerId) {
        grpMemberRepository.findByGroupAndUser(groupId, callerId)
                .filter(GrpMember::isActive)
                .orElseThrow(NotGroupMemberException::new);
    }

    private void requireOwner(UUID groupId, UUID callerId) {
        grpMemberRepository.findByGroupAndUser(groupId, callerId)
                .filter(m -> m.isOwner() && m.isActive())
                .orElseThrow(NotGroupOwnerException::new);
    }

    private GroupResponse toResponse(Group g) {
        return new GroupResponse(
                g.getId(),
                g.getTitle(),
                g.getDescription(),
                g.getTzId(),
                g.isLocked(),
                g.isShowParticipants(),
                g.getOwner().getId(),
                g.getVersion(),
                g.getCreatedAt(),
                g.getUpdatedAt()
        );
    }
}
