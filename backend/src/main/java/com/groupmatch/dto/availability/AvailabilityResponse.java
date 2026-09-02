package com.groupmatch.dto.availability;

import java.time.Instant;
import java.util.UUID;

public record AvailabilityResponse(
        UUID id,
        UUID groupId,
        UUID userId,
        Instant startsAt,
        Instant endsAt,
        String note,
        /**
         * {@code null} у одиночного слота. Поле добавлено вместе с сериями:
         * без него клиент не может отличить слот серии от одиночного, а
         * значит и не может предложить «удалить всю серию» — ручка
         * {@code ?scope=series} осталась бы недостижимой из интерфейса.
         */
        UUID seriesId,
        Instant createdAt
) {}
