package com.groupmatch;

import com.groupmatch.admin.AdminTest;
import com.groupmatch.auth.AuthTest;
import com.groupmatch.auth.GuestUpgradeTest;
import com.groupmatch.groups.GroupTest;
import com.groupmatch.groups.InviteTest;
import com.groupmatch.meetings.MeetingTest;
import com.groupmatch.notifications.NotificationPreferencesTest;
import com.groupmatch.notifications.NotificationTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    AuthTest.class,
    GuestUpgradeTest.class,
    AdminTest.class,
    GroupTest.class,
    InviteTest.class,
    MeetingTest.class,
    NotificationTest.class,
    NotificationPreferencesTest.class
})
public class IntegrationTestSuite {}
