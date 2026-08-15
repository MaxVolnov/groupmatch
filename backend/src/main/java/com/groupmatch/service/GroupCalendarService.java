package com.groupmatch.service;

import com.groupmatch.domain.GrpMember;
import com.groupmatch.domain.Group;
import com.groupmatch.domain.Meeting;
import com.groupmatch.dto.group.CalendarSubscriptionResponse;
import com.groupmatch.exception.GroupNotFoundException;
import com.groupmatch.exception.NotGroupMemberException;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Подписка на календарь группы: постоянный .ics-фид со всеми будущими
 * встречами, который календарный клиент перечитывает сам.
 *
 * Доступ — по токену группы в URL, а не по JWT: Google Calendar и Apple
 * Calendar при обновлении подписки не отправляют заголовок Authorization.
 * Модель та же, что у ссылок-приглашений: знание неугадываемого токена и есть
 * право доступа.
 *
 * Отдаём только будущие встречи: подписка — это «что впереди», а не архив,
 * и без отсечения фид рос бы неограниченно.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupCalendarService {

    /** Как часто календарному клиенту стоит перечитывать фид. */
    private static final int REFRESH_MINUTES = 60;

    private final GroupRepository groupRepository;
    private final GrpMemberRepository grpMemberRepository;
    private final MeetingRepository meetingRepository;
    private final IcsWriter icsWriter;

    @Value("${app.mail.base-url:http://localhost:3000}")
    private String appBaseUrl;

    @Value("${app.api-base-url:}")
    private String apiBaseUrl;

    /**
     * Сам фид. Вызывается без аутентификации — авторизует только токен.
     *
     * @throws GroupNotFoundException если группы нет или токен не совпал:
     *         одинаковый ответ на оба случая, чтобы по коду ответа нельзя было
     *         перебирать существующие группы
     */
    @Transactional(readOnly = true)
    public String buildFeed(UUID groupId, String token) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        if (!constantTimeEquals(group.getCalendarToken(), token)) {
            log.warn("Calendar feed rejected: bad token. groupId={}", groupId);
            throw new GroupNotFoundException(groupId);
        }

        List<Meeting> upcoming = meetingRepository
                .findByGroupIdAndEndsAtAfterOrderByStartsAtAsc(groupId, Instant.now());
        log.debug("Calendar feed served. groupId={}, events={}", groupId, upcoming.size());

        return icsWriter.feed(group.getTitle(), upcoming, REFRESH_MINUTES);
    }

    /** Ссылка на подписку — только активному участнику группы. */
    @Transactional(readOnly = true)
    public CalendarSubscriptionResponse getSubscription(UUID groupId, UUID callerId) {
        grpMemberRepository.findByGroupAndUser(groupId, callerId)
                .filter(GrpMember::isActive)
                .orElseThrow(NotGroupMemberException::new);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        String httpsUrl = feedBaseUrl()
                + "/api/v1/groups/" + groupId + "/calendar.ics?token=" + group.getCalendarToken();
        // webcal:// — тот же URL, но по схеме, которую ОС отдаёт календарю,
        // а не браузеру: клик подписывает, а не скачивает разовый файл.
        String webcalUrl = httpsUrl.replaceFirst("^https?://", "webcal://");

        return new CalendarSubscriptionResponse(httpsUrl, webcalUrl, REFRESH_MINUTES);
    }

    /**
     * База для ссылки на фид — это адрес API, а не фронтенда: они живут на
     * разных хостах. app.api-base-url задаётся в проде явно;
     * пустое значение = деплой «в одном домене», берём базу приложения.
     */
    private String feedBaseUrl() {
        String base = apiBaseUrl == null || apiBaseUrl.isBlank() ? appBaseUrl : apiBaseUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
