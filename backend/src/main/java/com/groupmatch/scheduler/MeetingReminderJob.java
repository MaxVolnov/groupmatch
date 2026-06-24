package com.groupmatch.scheduler;

import com.groupmatch.domain.GrpMember;
import com.groupmatch.domain.Meeting;
import com.groupmatch.domain.MemberStatus;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.MeetingRepository;
import com.groupmatch.repository.UserRepository;
import com.groupmatch.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeetingReminderJob {

    private final MeetingRepository meetingRepository;
    private final GrpMemberRepository grpMemberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void sendReminders() {
        Instant from = Instant.now().plus(55, ChronoUnit.MINUTES);
        Instant to   = Instant.now().plus(65, ChronoUnit.MINUTES);

        var meetings = meetingRepository.findUpcomingForReminder(from, to);
        if (meetings.isEmpty()) return;

        log.info("MeetingReminderJob: found {} meetings to remind", meetings.size());

        for (Meeting meeting : meetings) {
            try {
                processMeeting(meeting);
                meeting.setReminderSent(true);
                meetingRepository.save(meeting);
            } catch (Exception e) {
                log.error("Failed to process reminder for meetingId={}. error={}",
                        meeting.getId(), e.getMessage(), e);
            }
        }
    }

    private void processMeeting(Meeting meeting) {
        String groupTitle = groupRepository.findById(meeting.getGroupId())
                .map(g -> g.getTitle())
                .orElse("your group");

        for (GrpMember member : grpMemberRepository.findByGroupAndStatus(meeting.getGroupId(), MemberStatus.ACTIVE)) {
            userRepository.findById(member.getUser()).ifPresent(user -> {
                if (user.isGuest()) return;
                try {
                    emailService.sendMeetingReminderEmail(
                        user.getEmail(),
                        user.getDisplayName(),
                        meeting.getTitle(),
                        groupTitle,
                        meeting.getStartsAt(),
                        user.getTzId()
                    );
                } catch (Exception e) {
                    log.warn("Failed to send reminder to userId={}. error={}", user.getId(), e.getMessage());
                }
            });
        }
    }
}
