package com.groupmatch.dto.availability;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Описание окна для массовой очистки: «убери у меня все утра вторников и
 * четвергов в этом месяце».
 *
 * Форма нарочно совпадает с {@link AvailabilitySeriesRequest} — тем же
 * набором дней недели и тем же окном, которым серия заводилась, её можно и
 * убрать. Но принадлежность к серии здесь не учитывается: очистка работает
 * по времени, поэтому подчищает и слоты, наставленные вручную.
 *
 * @param dryRun посчитать, но не удалять
 */
public record AvailabilityBulkClearRequest(
        @NotEmpty @Size(max = 7) Set<DayOfWeek> daysOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @NotNull String timeZone,
        boolean dryRun
) {}
