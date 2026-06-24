package com.groupmatch.service;

import com.groupmatch.domain.NotificationPreferences;
import com.groupmatch.dto.notification.NotificationPreferencesResponse;
import com.groupmatch.dto.notification.UpdateNotificationPreferencesRequest;
import com.groupmatch.repository.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository prefsRepository;

    @Transactional
    public NotificationPreferences getOrCreate(UUID userId) {
        return prefsRepository.findById(userId)
                .orElseGet(() -> prefsRepository.save(
                        NotificationPreferences.defaultsFor(userId)));
    }

    @Transactional
    public NotificationPreferencesResponse update(UUID userId,
                                                   UpdateNotificationPreferencesRequest req) {
        NotificationPreferences prefs = getOrCreate(userId);
        if (req.emailMemberJoined()    != null) prefs.setEmailMemberJoined(req.emailMemberJoined());
        if (req.emailMeetingReminder() != null) prefs.setEmailMeetingReminder(req.emailMeetingReminder());
        if (req.inappMemberJoined()    != null) prefs.setInappMemberJoined(req.inappMemberJoined());
        if (req.inappMeetingCreated()  != null) prefs.setInappMeetingCreated(req.inappMeetingCreated());
        return NotificationPreferencesResponse.from(prefsRepository.save(prefs));
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getResponse(UUID userId) {
        return NotificationPreferencesResponse.from(getOrCreate(userId));
    }
}
