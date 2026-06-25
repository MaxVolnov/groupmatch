package com.groupmatch;

import com.groupmatch.admin.AdminTest;
import com.groupmatch.auth.AuthTest;
import com.groupmatch.auth.GuestUpgradeTest;
import com.groupmatch.availability.AvailabilityTest;
import com.groupmatch.groups.GroupErrorPathTest;
import com.groupmatch.groups.GroupTest;
import com.groupmatch.groups.InviteTest;
import com.groupmatch.meetings.MeetingTest;
import com.groupmatch.notifications.NotificationPreferencesTest;
import com.groupmatch.notifications.NotificationTest;
import com.groupmatch.payments.PaymentTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    AuthTest.class,
    GuestUpgradeTest.class,
    AdminTest.class,
    GroupTest.class,
    GroupErrorPathTest.class,
    InviteTest.class,
    MeetingTest.class,
    NotificationTest.class,
    NotificationPreferencesTest.class,
    AvailabilityTest.class,
    PaymentTest.class
})
public class IntegrationTestSuite {}
