package com.groupmatch.controller;

import com.groupmatch.dto.auth.UpdateProfileRequest;
import com.groupmatch.dto.auth.UserResponse;
import com.groupmatch.dto.notification.NotificationPreferencesResponse;
import com.groupmatch.dto.notification.UpdateNotificationPreferencesRequest;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.NotificationPreferencesService;
import com.groupmatch.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final NotificationPreferencesService notificationPreferencesService;

    @GetMapping
    public UserResponse getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMe(principal.getId());
    }

    @PatchMapping
    public UserResponse updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateMe(principal.getId(), req);
    }

    @GetMapping("/notification-preferences")
    public NotificationPreferencesResponse getNotificationPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        return notificationPreferencesService.getResponse(principal.getId());
    }

    @PatchMapping("/notification-preferences")
    public NotificationPreferencesResponse updateNotificationPreferences(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateNotificationPreferencesRequest request) {
        return notificationPreferencesService.update(principal.getId(), request);
    }
}
