package com.groupmatch.controller;

import com.groupmatch.dto.meeting.MeetingRequest;
import com.groupmatch.dto.meeting.MeetingResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable UUID groupId,
                                  @Valid @RequestBody MeetingRequest req) {
        return meetingService.createMeeting(groupId, principal.getId(), req);
    }

    @GetMapping
    public List<MeetingResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID groupId) {
        return meetingService.listMeetings(groupId, principal.getId());
    }

    @GetMapping("/{meetingId}")
    public MeetingResponse get(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable UUID groupId,
                               @PathVariable UUID meetingId) {
        return meetingService.getMeeting(meetingId, groupId, principal.getId());
    }

    @PutMapping("/{meetingId}")
    public MeetingResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable UUID groupId,
                                  @PathVariable UUID meetingId,
                                  @Valid @RequestBody MeetingRequest req) {
        return meetingService.updateMeeting(meetingId, groupId, principal.getId(), req);
    }

    @DeleteMapping("/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID groupId,
                       @PathVariable UUID meetingId) {
        meetingService.deleteMeeting(meetingId, groupId, principal.getId());
    }

    @GetMapping("/{meetingId}/export.ics")
    public ResponseEntity<String> exportIcs(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID groupId,
                                            @PathVariable UUID meetingId) {
        String ics = meetingService.exportIcs(meetingId, groupId, principal.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/calendar; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meeting.ics\"")
                .body(ics);
    }
}
