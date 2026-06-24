package com.groupmatch.dto.notification;

import com.groupmatch.domain.Notification;
import com.groupmatch.domain.NotificationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        Map<String, String> payload,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getPayload(), n.isRead(), n.getCreatedAt());
    }
}
