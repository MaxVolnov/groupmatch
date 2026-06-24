CREATE TABLE notification_preferences (
    user_id                UUID    PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    email_member_joined    BOOLEAN NOT NULL DEFAULT TRUE,
    email_meeting_reminder BOOLEAN NOT NULL DEFAULT TRUE,
    inapp_member_joined    BOOLEAN NOT NULL DEFAULT TRUE,
    inapp_meeting_created  BOOLEAN NOT NULL DEFAULT TRUE
);
