package com.groupmatch.dto.availability;

import java.util.UUID;

/**
 * Сами слоты обратно не возвращаются: их может быть две сотни, а клиенту
 * после создания серии всё равно нужен свежий список целиком — он идёт за
 * ним в {@code GET /availability/my}.
 */
public record AvailabilitySeriesResponse(UUID seriesId, int createdCount) {}
