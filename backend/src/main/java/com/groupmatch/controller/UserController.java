package com.groupmatch.controller;

import com.groupmatch.dto.auth.AccountDeletionPreviewResponse;
import com.groupmatch.dto.auth.DeleteAccountRequest;
import com.groupmatch.dto.auth.UpdateProfileRequest;
import com.groupmatch.dto.auth.UserResponse;
import com.groupmatch.dto.notification.NotificationPreferencesResponse;
import com.groupmatch.dto.notification.UpdateNotificationPreferencesRequest;
import com.groupmatch.dto.plan.PlanInfoResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.AccountDeletionService;
import com.groupmatch.service.GroupService;
import com.groupmatch.service.NotificationPreferencesService;
import com.groupmatch.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final NotificationPreferencesService notificationPreferencesService;
    private final GroupService groupService;
    private final AccountDeletionService accountDeletionService;

    @GetMapping
    public UserResponse getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMe(principal.getId());
    }

    @PatchMapping
    public UserResponse updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateMe(principal.getId(), req);
    }

    @GetMapping("/plan")
    public PlanInfoResponse getPlanInfo(@AuthenticationPrincipal UserPrincipal principal) {
        return groupService.getPlanInfo(principal.getId(), principal.getPlan());
    }

    /**
     * Что произойдёт с группами, если удалить аккаунт.
     *
     * Отдельный запрос, а не поле в {@code GET /me}: считается он по составу
     * всех групп человека и нужен ровно один раз — когда открывают
     * подтверждение.
     */
    @GetMapping("/deletion-preview")
    public AccountDeletionPreviewResponse deletionPreview(@AuthenticationPrincipal UserPrincipal principal) {
        return accountDeletionService.previewDeletion(principal.getId());
    }

    /**
     * Удаление собственного аккаунта.
     *
     * Мягкое: строка остаётся, вход закрывается, через срок отсрочки данные
     * обезличивает джоба. Тело с паролем обязательно для всех, кроме гостей.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal UserPrincipal principal,
                         @RequestBody(required = false) DeleteAccountRequest request) {
        accountDeletionService.deleteOwnAccount(
                principal.getId(), request == null ? null : request.password());
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
