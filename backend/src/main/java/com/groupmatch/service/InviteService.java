package com.groupmatch.service;

import com.groupmatch.domain.*;
import com.groupmatch.dto.invite.CreateInviteRequest;
import com.groupmatch.dto.invite.InviteResponse;
import com.groupmatch.exception.*;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.InviteRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private static final int TOKEN_BYTES = 24; // 48 hex chars
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteRepository inviteRepository;
    private final GrpMemberRepository grpMemberRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final NotificationService notificationService;

    @Transactional
    public InviteResponse createInvite(UUID groupId, UUID callerId, Plan callerPlan,
                                       CreateInviteRequest req) {
        requireOwner(groupId, callerId);

        // Rate-limit: invitesPerHour per plan
        int limit = callerPlan.limits().invitesPerHour();
        if (limit != Integer.MAX_VALUE) {
            Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
            long recentCount = inviteRepository
                    .countByGroupIdAndCreatedByAndCreatedAtAfter(groupId, callerId, oneHourAgo);
            if (recentCount >= limit) {
                throw new PlanLimitExceededException(
                        "Rate limit: max " + limit + " invites per hour for " + callerPlan + " plan");
            }
        }

        Instant expiresAt = req.expiresAt() != null
                ? req.expiresAt()
                : Instant.now().plus(30, ChronoUnit.DAYS);

        Invite invite = new Invite();
        invite.setGroupId(groupId);
        invite.setToken(generateToken());
        invite.setCreatedBy(callerId);
        invite.setExpiresAt(expiresAt);
        invite.setMaxUses(req.maxUses());

        return toResponse(inviteRepository.save(invite));
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listInvites(UUID groupId, UUID callerId) {
        requireOwner(groupId, callerId);
        return inviteRepository.findByGroupIdAndRevokedFalse(groupId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void revokeInvite(UUID inviteId, UUID groupId, UUID callerId) {
        requireOwner(groupId, callerId);
        Invite invite = inviteRepository.findById(inviteId)
                .filter(i -> i.getGroupId().equals(groupId))
                .orElseThrow(() -> new InviteNotFoundException(inviteId));
        invite.setRevoked(true);
        inviteRepository.save(invite);
    }

    @Transactional
    public InviteResponse joinByToken(String token, UUID callerId, Plan callerPlan) {
        Invite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new InviteNotFoundException(token));

        if (!invite.isValid()) {
            throw new InviteInvalidException();
        }

        UUID groupId = invite.getGroupId();

        Optional<GrpMember> existingMember = grpMemberRepository.findByGroupAndUser(groupId, callerId);
        if (existingMember.isPresent()) {
            MemberStatus status = existingMember.get().getStatus();
            if (status == MemberStatus.BANNED) throw new MemberBannedException();
            // Already active — idempotent join: redirect to the group, no error shown.
            if (status == MemberStatus.ACTIVE) return toResponse(invite);
        }

        // Check group member limit against the OWNER's plan
        // (owner is the one whose plan governs the group capacity)
        // We rely on addMember plan-check — here we just do a best-effort check
        GrpMember ownerMembership = grpMemberRepository
                .findByGroupAndStatus(groupId, MemberStatus.ACTIVE)
                .stream().filter(GrpMember::isOwner).findFirst()
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // Reuse existing LEFT member record if present, otherwise create new
        GrpMember member = existingMember
                .orElse(new GrpMember(groupId, callerId, GroupRole.MEMBER, MemberStatus.ACTIVE));
        member.setStatus(MemberStatus.ACTIVE);
        grpMemberRepository.save(member);

        invite.setCurrentUses(invite.getCurrentUses() + 1);
        inviteRepository.save(invite);

        UUID ownerId = ownerMembership.getUser();
        if (!ownerId.equals(callerId)) {
            userRepository.findById(callerId).ifPresent(joiner ->
                groupRepository.findById(groupId).ifPresent(group ->
                    notificationService.create(ownerId, NotificationType.MEMBER_JOINED, Map.of(
                        "groupId", groupId.toString(),
                        "groupTitle", group.getTitle(),
                        "joinerId", callerId.toString(),
                        "joinerName", joiner.getDisplayName()
                    ))
                )
            );
        }

        return toResponse(invite);
    }

    // --- helpers ---

    private void requireOwner(UUID groupId, UUID callerId) {
        grpMemberRepository.findByGroupAndUser(groupId, callerId)
                .filter(m -> m.isOwner() && m.isActive())
                .orElseThrow(NotGroupOwnerException::new);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private InviteResponse toResponse(Invite i) {
        return new InviteResponse(i.getId(), i.getGroupId(), i.getToken(), i.getCreatedBy(),
                i.getCreatedAt(), i.getExpiresAt(), i.getMaxUses(), i.getCurrentUses(), i.isRevoked());
    }
}
