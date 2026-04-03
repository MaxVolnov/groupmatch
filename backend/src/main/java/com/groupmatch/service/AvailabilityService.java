package com.groupmatch.service;

import com.groupmatch.domain.*;
import com.groupmatch.dto.availability.AvailabilityRequest;
import com.groupmatch.dto.availability.AvailabilityResponse;
import com.groupmatch.dto.availability.HeatmapResponse;
import com.groupmatch.dto.availability.HeatmapResponse.HeatmapSlot;
import com.groupmatch.exception.*;
import com.groupmatch.repository.AvailabilityRepository;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private static final int DEFAULT_GRANULARITY_MINUTES = 30;

    private final AvailabilityRepository availabilityRepository;
    private final GrpMemberRepository grpMemberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    @Transactional
    public AvailabilityResponse addSlot(UUID groupId, UUID callerId, Plan callerPlan,
                                        AvailabilityRequest req) {
        validateSlotTimes(req.startsAt(), req.endsAt());
        GrpMember membership = requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        if (group.isLocked() && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }

        long existing = availabilityRepository.countByGroupIdAndUserId(groupId, callerId);
        int maxSlots = callerPlan.limits().maxSlotsPerMember();
        if (existing >= maxSlots) {
            throw new PlanLimitExceededException(
                    "Plan limit reached: max " + maxSlots + " slots per group for " + callerPlan + " plan");
        }

        Availability slot = availabilityRepository.save(
                new Availability(groupId, callerId, req.startsAt(), req.endsAt(), req.note()));
        return toResponse(slot);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getMySlots(UUID groupId, UUID callerId) {
        requireActiveMember(groupId, callerId);
        return availabilityRepository.findByGroupIdAndUserId(groupId, callerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AvailabilityResponse updateSlot(UUID slotId, UUID groupId, UUID callerId,
                                           AvailabilityRequest req) {
        validateSlotTimes(req.startsAt(), req.endsAt());
        GrpMember membership = requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        Availability slot = availabilityRepository.findById(slotId)
                .filter(s -> s.getGroupId().equals(groupId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (!slot.getUserId().equals(callerId)) {
            // Members can only edit their own slots; OWNER can edit any
            if (!membership.isOwner()) {
                throw new NotGroupOwnerException();
            }
        }
        if (group.isLocked() && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }

        slot.setStartsAt(req.startsAt());
        slot.setEndsAt(req.endsAt());
        slot.setNote(req.note());
        return toResponse(availabilityRepository.save(slot));
    }

    @Transactional
    public void deleteSlot(UUID slotId, UUID groupId, UUID callerId) {
        GrpMember membership = requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        Availability slot = availabilityRepository.findById(slotId)
                .filter(s -> s.getGroupId().equals(groupId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (!slot.getUserId().equals(callerId) && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }
        if (group.isLocked() && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }

        availabilityRepository.delete(slot);
    }

    @Transactional(readOnly = true)
    public HeatmapResponse getHeatmap(UUID groupId, UUID callerId,
                                      Instant from, Instant to, Integer granularityMinutes) {
        requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        int granularity = (granularityMinutes != null && granularityMinutes > 0)
                ? granularityMinutes : DEFAULT_GRANULARITY_MINUTES;

        if (from == null) from = Instant.now().truncatedTo(ChronoUnit.DAYS);
        if (to == null) to = from.plus(7L, ChronoUnit.DAYS);

        List<Availability> slots = availabilityRepository
                .findByGroupIdAndStartsAtLessThanAndEndsAtGreaterThan(groupId, to, from);

        // Fetch user display names if showParticipants
        Map<UUID, String> userNames = Collections.emptyMap();
        if (group.isShowParticipants() && !slots.isEmpty()) {
            Set<UUID> userIds = slots.stream().map(Availability::getUserId).collect(Collectors.toSet());
            userNames = userRepository.findAllById(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getDisplayName));
        }

        List<HeatmapSlot> heatmapSlots = computeBuckets(slots, from, to, granularity,
                group.isShowParticipants(), userNames);

        return new HeatmapResponse(heatmapSlots, granularity, from, to);
    }

    // --- private helpers ---

    private List<HeatmapSlot> computeBuckets(List<Availability> slots,
                                              Instant from, Instant to,
                                              int granularityMinutes,
                                              boolean showParticipants,
                                              Map<UUID, String> userNames) {
        List<HeatmapSlot> result = new ArrayList<>();
        Instant bucketStart = from;
        long bucketSize = (long) granularityMinutes * 60;

        while (bucketStart.isBefore(to)) {
            Instant bucketEnd = bucketStart.plusSeconds(bucketSize);
            if (bucketEnd.isAfter(to)) bucketEnd = to;

            final Instant bs = bucketStart;
            final Instant be = bucketEnd;

            // Users available in this bucket (slot overlaps bucket)
            List<UUID> available = slots.stream()
                    .filter(s -> s.getStartsAt().isBefore(be) && s.getEndsAt().isAfter(bs))
                    .map(Availability::getUserId)
                    .distinct()
                    .toList();

            if (!available.isEmpty()) {
                List<UUID> memberIds = showParticipants ? available : null;
                List<String> displayNames = showParticipants
                        ? available.stream().map(id -> userNames.getOrDefault(id, id.toString())).toList()
                        : null;
                result.add(new HeatmapSlot(bs, be, available.size(), memberIds, displayNames));
            }

            bucketStart = bucketEnd;
        }

        return result;
    }

    private GrpMember requireActiveMember(UUID groupId, UUID callerId) {
        return grpMemberRepository.findByGroupAndUser(groupId, callerId)
                .filter(GrpMember::isActive)
                .orElseThrow(NotGroupMemberException::new);
    }

    private Group loadGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
    }

    private void validateSlotTimes(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("ends_at must be after starts_at");
        }
        long minutes = ChronoUnit.MINUTES.between(startsAt, endsAt);
        if (minutes < 5) {
            throw new IllegalArgumentException("Slot must be at least 5 minutes");
        }
        if (minutes > 48 * 60) {
            throw new IllegalArgumentException("Slot cannot exceed 48 hours");
        }
    }

    private AvailabilityResponse toResponse(Availability s) {
        return new AvailabilityResponse(s.getId(), s.getGroupId(), s.getUserId(),
                s.getStartsAt(), s.getEndsAt(), s.getNote(), s.getCreatedAt());
    }
}
