package com.groupmatch.service;

import com.groupmatch.domain.NotificationPreferences;
import com.groupmatch.dto.notification.NotificationPreferencesResponse;
import com.groupmatch.dto.notification.UpdateNotificationPreferencesRequest;
import com.groupmatch.repository.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository prefsRepository;

    @Transactional
    public NotificationPreferences getOrCreate(UUID userId) {
        return prefsRepository.findById(userId)
                .orElseGet(() -> prefsRepository.save(
                        NotificationPreferences.defaultsFor(userId)));
    }

    /**
     * Пакетный вариант {@link #getOrCreate(UUID)}: один SELECT ... IN на всех
     * вместо запроса на каждого. Нужен там, где настройки читаются по списку
     * участников — например при рассылке уведомлений о новой встрече.
     *
     * Гарантия: в результате есть запись для каждого переданного id.
     *
     * @return настройки по userId; пустая карта, если список пуст
     */
    @Transactional
    public Map<UUID, NotificationPreferences> getOrCreateAll(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();

        Set<UUID> ids = new LinkedHashSet<>(userIds);
        Map<UUID, NotificationPreferences> byUser = new HashMap<>();
        prefsRepository.findAllById(ids).forEach(p -> byUser.put(p.getUserId(), p));

        List<NotificationPreferences> missing = ids.stream()
                .filter(id -> !byUser.containsKey(id))
                .map(NotificationPreferences::defaultsFor)
                .toList();
        if (!missing.isEmpty()) {
            prefsRepository.saveAll(missing).forEach(p -> byUser.put(p.getUserId(), p));
        }
        return byUser;
    }

    @Transactional
    public NotificationPreferencesResponse update(UUID userId,
                                                   UpdateNotificationPreferencesRequest req) {
        NotificationPreferences prefs = getOrCreate(userId);
        if (req.emailMemberJoined()    != null) prefs.setEmailMemberJoined(req.emailMemberJoined());
        if (req.emailMeetingReminder() != null) prefs.setEmailMeetingReminder(req.emailMeetingReminder());
        if (req.inappMemberJoined()    != null) prefs.setInappMemberJoined(req.inappMemberJoined());
        if (req.inappMeetingCreated()  != null) prefs.setInappMeetingCreated(req.inappMeetingCreated());
        return NotificationPreferencesResponse.from(prefsRepository.save(prefs));
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getResponse(UUID userId) {
        return NotificationPreferencesResponse.from(getOrCreate(userId));
    }
}
