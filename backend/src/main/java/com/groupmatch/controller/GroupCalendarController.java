package com.groupmatch.controller;

import com.groupmatch.dto.group.CalendarSubscriptionResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.GroupCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@RequiredArgsConstructor
public class GroupCalendarController {

    private final GroupCalendarService groupCalendarService;

    /**
     * Фид подписки. Без JWT: календарные клиенты не умеют слать Authorization,
     * доступ авторизует токен группы в query. Неверный токен → 404, как и
     * несуществующая группа.
     */
    @GetMapping("/calendar.ics")
    public ResponseEntity<String> feed(@PathVariable UUID groupId,
                                       @RequestParam(name = "token", required = false) String token) {
        String ics = groupCalendarService.buildFeed(groupId, token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/calendar; charset=UTF-8")
                // inline: клиент подписывается, а не сохраняет разовый файл
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"groupmatch.ics\"")
                .cacheControl(CacheControl.maxAge(15, TimeUnit.MINUTES))
                .body(ics);
    }

    /** Ссылку на подписку показываем только активному участнику группы. */
    @GetMapping("/calendar-subscription")
    public CalendarSubscriptionResponse subscription(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable UUID groupId) {
        return groupCalendarService.getSubscription(groupId, principal.getId());
    }
}
