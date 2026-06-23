package com.groupmatch.service;

import com.groupmatch.domain.Feedback;
import com.groupmatch.domain.FeedbackCategory;
import com.groupmatch.domain.Group;
import com.groupmatch.domain.MemberStatus;
import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Role;
import com.groupmatch.domain.User;
import com.groupmatch.dto.admin.AdminFeedbackPageResponse;
import com.groupmatch.dto.admin.AdminFeedbackResponse;
import com.groupmatch.dto.admin.AdminGroupPageResponse;
import com.groupmatch.dto.admin.AdminGroupResponse;
import com.groupmatch.dto.admin.AdminUserResponse;
import com.groupmatch.dto.admin.AdminUsersPageResponse;
import com.groupmatch.exception.FeedbackNotFoundException;
import com.groupmatch.exception.ForbiddenException;
import com.groupmatch.exception.GroupNotFoundException;
import com.groupmatch.exception.UserNotFoundException;
import com.groupmatch.repository.FeedbackRepository;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final GroupRepository groupRepository;
    private final GrpMemberRepository grpMemberRepository;

    // ── Users ─────────────────────────────────────────────────────────────────

    public AdminUsersPageResponse getUsers(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> result = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                        search, search, pageable);
        return new AdminUsersPageResponse(
                result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Transactional
    public void changeUserRole(UUID userId, Role newRole, UUID requesterId) {
        if (userId.equals(requesterId)) throw new ForbiddenException("Cannot change own role");
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Role oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        log.info("User role changed. userId={}, {} -> {}, by={}", userId, oldRole, newRole, requesterId);
    }

    @Transactional
    public void changeUserPlan(UUID userId, Plan newPlan) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Plan oldPlan = user.getPlan();
        user.setPlan(newPlan);
        userRepository.save(user);
        log.info("User plan changed. userId={}, {} -> {}", userId, oldPlan, newPlan);
    }

    @Transactional
    public void banUser(UUID userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException("Cannot ban ADMIN");
        }
        user.setBanned(true);
        user.setBanReason(reason);
        userRepository.save(user);
        log.info("User banned. userId={}, reason={}", userId, reason);
    }

    @Transactional
    public void unbanUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.setBanned(false);
        user.setBanReason(null);
        userRepository.save(user);
        log.info("User unbanned. userId={}", userId);
    }

    private AdminUserResponse toDto(User u) {
        return new AdminUserResponse(
                u.getId(), u.getEmail(), u.getDisplayName(),
                u.getRole(), u.getPlan(), u.isGuest(), u.isBanned(), u.getCreatedAt()
        );
    }

    // ── Feedback ──────────────────────────────────────────────────────────────

    public AdminFeedbackPageResponse getFeedback(FeedbackCategory category, Boolean resolved, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Feedback> result;
        if (category != null && resolved != null) {
            result = feedbackRepository.findByCategoryAndResolved(category, resolved, pageable);
        } else if (category != null) {
            result = feedbackRepository.findByCategory(category, pageable);
        } else if (resolved != null) {
            result = feedbackRepository.findByResolved(resolved, pageable);
        } else {
            result = feedbackRepository.findAll(pageable);
        }
        return new AdminFeedbackPageResponse(
                result.getContent().stream().map(this::toFeedbackDto).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Transactional
    public void resolveFeedback(UUID id, UUID resolvedBy) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new FeedbackNotFoundException(id));
        feedback.setResolved(true);
        feedback.setResolvedAt(Instant.now());
        feedback.setResolvedBy(resolvedBy);
        feedbackRepository.save(feedback);
        log.info("Feedback resolved. id={}, by={}", id, resolvedBy);
    }

    @Transactional
    public void unresolveFeedback(UUID id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new FeedbackNotFoundException(id));
        feedback.setResolved(false);
        feedback.setResolvedAt(null);
        feedback.setResolvedBy(null);
        feedbackRepository.save(feedback);
        log.info("Feedback unresolved. id={}", id);
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    public AdminGroupPageResponse getGroups(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Group> result = (search == null || search.isBlank())
                ? groupRepository.findAll(pageable)
                : groupRepository.findByTitleContainingIgnoreCase(search, pageable);
        return new AdminGroupPageResponse(
                result.getContent().stream().map(this::toGroupDto).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Transactional
    public void deleteGroup(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        groupRepository.delete(group);
        log.info("Group force-deleted by admin. groupId={}", groupId);
    }

    private AdminGroupResponse toGroupDto(Group g) {
        // TODO: optimize with JOIN COUNT if needed
        int memberCount = (int) grpMemberRepository.countByGroupAndStatus(g.getId(), MemberStatus.ACTIVE);
        return new AdminGroupResponse(
                g.getId(), g.getTitle(), g.getDescription(), g.getTzId(),
                g.getOwner().getId(), g.getOwner().getEmail(), g.getOwner().getDisplayName(),
                memberCount, g.isLocked(), g.getCreatedAt()
        );
    }

    private AdminFeedbackResponse toFeedbackDto(Feedback f) {
        User author = f.getUser();
        return new AdminFeedbackResponse(
                f.getId(),
                f.getCategory().name(),
                f.getMessage(),
                author != null ? author.getEmail() : null,
                author != null ? author.getDisplayName() : null,
                f.isResolved(),
                f.getResolvedAt(),
                f.getCreatedAt()
        );
    }
}
