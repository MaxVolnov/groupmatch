package com.groupmatch.dto.group;

/**
 * Ссылки на .ics-подписку календаря группы.
 *
 * @param url            https-адрес фида — для копирования в любой клиент
 * @param webcalUrl      тот же адрес по схеме webcal:// — открывает календарь ОС
 * @param refreshMinutes рекомендованный интервал обновления, зашит в сам фид
 */
public record CalendarSubscriptionResponse(
        String url,
        String webcalUrl,
        int refreshMinutes
) {}
