package com.groupmatch.dto.availability;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Новое время слота или всей его серии.
 *
 * Время настенное и без даты, зона отдельным полем — как и при создании
 * серии. Дата у каждого слота своя и не меняется: вторники остаются
 * вторниками. Прислать сюда {@code Instant} значило бы задать одному слоту
 * абсолютный момент, а всей серии — одинаковый сдвиг, который на неделе
 * перевода часов увёл бы половину слотов на час.
 */
public record AvailabilityTimeRequest(
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull String timeZone
) {}
