package com.groupmatch.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_member_joined", nullable = false)
    private boolean emailMemberJoined = true;

    @Column(name = "email_meeting_reminder", nullable = false)
    private boolean emailMeetingReminder = true;

    @Column(name = "inapp_member_joined", nullable = false)
    private boolean inappMemberJoined = true;

    @Column(name = "inapp_meeting_created", nullable = false)
    private boolean inappMeetingCreated = true;

    public static NotificationPreferences defaultsFor(UUID userId) {
        NotificationPreferences p = new NotificationPreferences();
        p.userId = userId;
        return p;
    }
}
