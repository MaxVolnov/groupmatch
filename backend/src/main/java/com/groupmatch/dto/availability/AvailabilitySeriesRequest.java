package com.groupmatch.dto.availability;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Правило повторения — только на входе. В базе оно не сохраняется: сервис
 * разворачивает его в обычные слоты и забывает.
 *
 * Время здесь настенное и без зоны, зона отдельным полем. Это не небрежность,
 * а суть: «каждый вторник в 10:00» — утверждение про местное время, и оно
 * обязано пережить перевод часов. Пришли бы сюда {@code Instant}, переводить
 * было бы уже нечего.
 */
public record AvailabilitySeriesRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotEmpty @Size(max = 7) Set<DayOfWeek> daysOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull String timeZone
) {}
