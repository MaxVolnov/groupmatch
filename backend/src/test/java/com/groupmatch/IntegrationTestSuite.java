package com.groupmatch;

import com.groupmatch.admin.AdminTest;
import com.groupmatch.auth.AuthTest;
import com.groupmatch.auth.GuestCleanupTest;
import com.groupmatch.auth.GuestUpgradeTest;
import com.groupmatch.auth.LocaleTest;
import com.groupmatch.availability.AvailabilityTest;
import com.groupmatch.groups.GroupErrorPathTest;
import com.groupmatch.groups.GroupTest;
import com.groupmatch.groups.InviteTest;
import com.groupmatch.groups.MemberLimitEnforcedTest;
import com.groupmatch.groups.MemberLimitFlagTest;
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
    AuthTest.class,
    GuestUpgradeTest.class,
    GuestCleanupTest.class,
    LocaleTest.class,
    AdminTest.class,
    GroupTest.class,
    GroupErrorPathTest.class,
    InviteTest.class,
    MemberLimitFlagTest.class,
    MemberLimitEnforcedTest.class,
    MeetingTest.class,
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
