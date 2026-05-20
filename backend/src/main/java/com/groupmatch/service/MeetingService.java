package com.groupmatch.service;

import com.groupmatch.domain.GrpMember;
import com.groupmatch.domain.Meeting;
import com.groupmatch.dto.meeting.MeetingRequest;
import com.groupmatch.dto.meeting.MeetingResponse;
import com.groupmatch.exception.*;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final GrpMemberRepository grpMemberRepository;

    @Transactional
    public MeetingResponse createMeeting(UUID groupId, UUID callerId, MeetingRequest req) {
        requireOwner(groupId, callerId);
        validateTimes(req);

        Meeting meeting = new Meeting();
        meeting.setGroupId(groupId);
        meeting.setCreatorId(callerId);
        meeting.setTitle(req.title());
        meeting.setDescription(req.description());
        meeting.setStartsAt(req.startsAt());
        meeting.setEndsAt(req.endsAt());

        return toResponse(meetingRepository.save(meeting));
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> listMeetings(UUID groupId, UUID callerId) {
        requireActiveMember(groupId, callerId);
        return meetingRepository.findByGroupIdOrderByStartsAtDesc(groupId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID meetingId, UUID groupId, UUID callerId) {
        requireActiveMember(groupId, callerId);
        return meetingRepository.findById(meetingId)
                .filter(m -> m.getGroupId().equals(groupId))
                .map(this::toResponse)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    }

    @Transactional
    public MeetingResponse updateMeeting(UUID meetingId, UUID groupId, UUID callerId,
                                         MeetingRequest req) {
        requireOwner(groupId, callerId);
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
        requireOwner(groupId, callerId);
        meetingRepository.findById(meetingId)
                .filter(m -> m.getGroupId().equals(groupId))
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        meetingRepository.deleteById(meetingId);
    }

    @Transactional(readOnly = true)
    public String exportIcs(UUID meetingId, UUID groupId, UUID callerId) {
        requireActiveMember(groupId, callerId);
        Meeting meeting = meetingRepository.findById(meetingId)
                .filter(m -> m.getGroupId().equals(groupId))
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
        return buildIcs(meeting);
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

    private void validateTimes(MeetingRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new IllegalArgumentException("ends_at must be after starts_at");
        }
    }

    private static final DateTimeFormatter ICS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private String buildIcs(Meeting m) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//GroupMatch//GroupMatch//EN\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(m.getId()).append("@groupmatch.app\r\n");
        sb.append("SUMMARY:").append(escapeIcs(m.getTitle())).append("\r\n");
        if (m.getDescription() != null && !m.getDescription().isBlank()) {
            sb.append("DESCRIPTION:").append(escapeIcs(m.getDescription())).append("\r\n");
        }
        sb.append("DTSTART:").append(ICS_FMT.format(m.getStartsAt())).append("\r\n");
        sb.append("DTEND:").append(ICS_FMT.format(m.getEndsAt())).append("\r\n");
        sb.append("DTSTAMP:").append(ICS_FMT.format(Instant.now())).append("\r\n");
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private String escapeIcs(String value) {
        return value.replace("\\", "\\\\")
                    .replace(";", "\\;")
                    .replace(",", "\\,")
                    .replace("\r\n", "\\n")
                    .replace("\n", "\\n");
    }

    private MeetingResponse toResponse(Meeting m) {
        return new MeetingResponse(m.getId(), m.getGroupId(), m.getCreatorId(),
                m.getTitle(), m.getDescription(), m.getStartsAt(), m.getEndsAt(), m.getCreatedAt());
    }
}
