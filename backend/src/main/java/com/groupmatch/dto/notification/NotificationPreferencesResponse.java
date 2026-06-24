package com.groupmatch.dto.notification;

import com.groupmatch.domain.NotificationPreferences;

public record NotificationPreferencesResponse(
        boolean emailMemberJoined,
        boolean emailMeetingReminder,
        boolean inappMemberJoined,
        boolean inappMeetingCreated
) {
    public static NotificationPreferencesResponse from(NotificationPreferences p) {
        return new NotificationPreferencesResponse(
                p.isEmailMemberJoined(), p.isEmailMeetingReminder(),
                p.isInappMemberJoined(), p.isInappMeetingCreated());
    }
}
