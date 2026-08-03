package com.groupmatch;

import com.groupmatch.admin.AdminTest;
import com.groupmatch.config.LoggingLevelsTest;
import com.groupmatch.auth.AuthTest;
import com.groupmatch.auth.GuestCleanupTest;
import com.groupmatch.auth.GuestUpgradeTest;
import com.groupmatch.auth.LocaleTest;
import com.groupmatch.auth.TrialPremiumTest;
import com.groupmatch.auth.TrialServiceTest;
import com.groupmatch.availability.AvailabilityTest;
import com.groupmatch.groups.GroupErrorPathTest;
import com.groupmatch.groups.GroupTest;
import com.groupmatch.groups.InviteTest;
import com.groupmatch.groups.MemberLimitEnforcedTest;
import com.groupmatch.groups.MemberLimitFlagTest;
import com.groupmatch.groups.SlotAndInviteLimitEnforcedTest;
import com.groupmatch.groups.SlotAndInviteLimitFlagTest;
import com.groupmatch.meetings.GroupCalendarFeedTest;
import com.groupmatch.meetings.MeetingNPlusOneTest;
import com.groupmatch.meetings.MeetingTest;
import com.groupmatch.notifications.NotificationPreferencesTest;
import com.groupmatch.notifications.NotificationTest;
import com.groupmatch.payments.PaymentTest;
import com.groupmatch.payments.YooKassaWebhookAuthTest;
import com.groupmatch.security.ClientIpResolverTest;
import com.groupmatch.security.RateLimitBypassTest;
import com.groupmatch.security.YooKassaWebhookVerifierTest;
import com.groupmatch.util.CidrMatcherTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    LoggingLevelsTest.class,
    AuthTest.class,
    GuestUpgradeTest.class,
    GuestCleanupTest.class,
    LocaleTest.class,
    TrialServiceTest.class,
    TrialPremiumTest.class,
    AdminTest.class,
    GroupTest.class,
    GroupErrorPathTest.class,
    InviteTest.class,
    MemberLimitFlagTest.class,
    MemberLimitEnforcedTest.class,
    SlotAndInviteLimitFlagTest.class,
    SlotAndInviteLimitEnforcedTest.class,
    MeetingTest.class,
    GroupCalendarFeedTest.class,
    MeetingNPlusOneTest.class,
    NotificationTest.class,
    NotificationPreferencesTest.class,
    AvailabilityTest.class,
    PaymentTest.class,
    CidrMatcherTest.class,
    ClientIpResolverTest.class,
    RateLimitBypassTest.class,
    YooKassaWebhookVerifierTest.class,
    YooKassaWebhookAuthTest.class
})
public class IntegrationTestSuite {}
