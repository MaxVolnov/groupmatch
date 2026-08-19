package com.groupmatch.service;

import com.groupmatch.domain.GrpMember;
import com.groupmatch.domain.Meeting;
import com.groupmatch.domain.MemberStatus;
import com.groupmatch.domain.NotificationPreferences;
import com.groupmatch.domain.NotificationType;
import com.groupmatch.dto.meeting.MeetingRequest;
import com.groupmatch.dto.meeting.MeetingResponse;
import com.groupmatch.exception.*;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final GroupAccessGuard groupAccessGuard;
    private final GrpMemberRepository grpMemberRepository;
    private final NotificationService notificationService;
    private final NotificationPreferencesService notificationPreferencesService;
    private final IcsWriter icsWriter;

    @Transactional
    public MeetingResponse createMeeting(UUID groupId, UUID callerId, MeetingRequest req) {
        groupAccessGuard.requireOwner(groupId, callerId);
        validateTimes(req);

        Meeting meeting = new Meeting();
        meeting.setGroupId(groupId);
        meeting.setCreatorId(callerId);
        meeting.setTitle(req.title());
        meeting.setDescription(req.description());
        meeting.setStartsAt(req.startsAt());
        meeting.setEndsAt(req.endsAt());

        Meeting saved = meetingRepository.save(meeting);

        notifyMembers(groupId, callerId, saved, req.title());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> listMeetings(UUID groupId, UUID callerId) {
        groupAccessGuard.requireActiveMember(groupId, callerId);
        return meetingRepository.findByGroupIdOrderByStartsAtDesc(groupId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID meetingId, UUID groupId, UUID callerId) {
        groupAccessGuard.requireActiveMember(groupId, callerId);
        return meetingRepository.findById(meetingId)
                .filter(m -> m.getGroupId().equals(groupId))
                .map(this::toResponse)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    }

    @Transactional
    public MeetingResponse updateMeeting(UUID meetingId, UUID groupId, UUID callerId,
                                         MeetingRequest req) {
        groupAccessGuard.requireOwner(groupId, callerId);
        validateTimes(req);

        Meeting meeting = meetingRepository.findById(meetingId)
                .filter(m -> m.getGroupId().equals(groupId))
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));

        meeting.setTitle(req.title());
        meeting.setDescription(req.description());
        meeting.setStartsAt(req.startsAt());
        meeting.setEndsAt(req.endsAt());

        return toResponse(meetingRepository.save(meeting));
    }

    @Transactional
    public void deleteMeeting(UUID meetingId, UUID groupId, UUID callerId) {
        groupAccessGuard.requireOwner(groupId, callerId);
        meetingRepository.findById(meetingId)
                .filter(m -> m.getGroupId().equals(groupId))
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        meetingRepository.deleteById(meetingId);
    }

    @Transactional(readOnly = true)
    public String exportIcs(UUID meetingId, UUID groupId, UUID callerId) {
        groupAccessGuard.requireActiveMember(groupId, callerId);
        Meeting meeting = meetingRepository.findById(meetingId)
                .filter(m -> m.getGroupId().equals(groupId))
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        return icsWriter.singleEvent(meeting);
    }

    // --- helpers ---

    /**
     * Уведомляет участников о новой встрече.
     *
     * Настройки уведомлений забираются одним запросом на всех: раньше здесь
     * был getOrCreate() в цикле, то есть SELECT на каждого участника — в
     * группе на 30 человек это 30 лишних round-trip'ов на одно создание
     * встречи. Сами вставки уведомлений остаются поштучными: это отдельная
     * строка на получателя, их не сократить.
     */
    private void notifyMembers(UUID groupId, UUID callerId, Meeting saved, String title) {
        List<UUID> recipients = grpMemberRepository
                .findByGroupAndStatus(groupId, MemberStatus.ACTIVE).stream()
                .map(GrpMember::getUser)
                .filter(userId -> !userId.equals(callerId))
                .toList();
        if (recipients.isEmpty()) return;

        Map<UUID, NotificationPreferences> prefs =
                notificationPreferencesService.getOrCreateAll(recipients);

        for (UUID userId : recipients) {
            // getOrCreateAll гарантирует запись на каждого; defaults — страховка,
            // чтобы дырка в настройках не роняла создание встречи целиком.
            NotificationPreferences p = prefs.getOrDefault(
                    userId, NotificationPreferences.defaultsFor(userId));
            if (!p.isInappMeetingCreated()) continue;

            notificationService.create(userId, NotificationType.MEETING_CREATED, Map.of(
                    "groupId", groupId.toString(),
                    "meetingId", saved.getId().toString(),
                    "meetingTitle", title
            ));
        }
    }

    private void validateTimes(MeetingRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new IllegalArgumentException("ends_at must be after starts_at");
        }
    }

    private MeetingResponse toResponse(Meeting m) {
        return new MeetingResponse(m.getId(), m.getGroupId(), m.getCreatorId(),
                m.getTitle(), m.getDescription(), m.getStartsAt(), m.getEndsAt(), m.getCreatedAt());
    }
}
