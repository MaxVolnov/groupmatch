package com.groupmatch.dto.notification;

public record UpdateNotificationPreferencesRequest(
        Boolean emailMemberJoined,
        Boolean emailMeetingReminder,
        Boolean inappMemberJoined,
        Boolean inappMeetingCreated
) {}
