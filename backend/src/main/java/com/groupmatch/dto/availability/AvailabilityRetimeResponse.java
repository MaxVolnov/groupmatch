package com.groupmatch.dto.availability;

import java.util.UUID;

/**
 * @param seriesId    серия, которую сдвинули
 * @param updatedCount сколько слотов затронуто
 */
public record AvailabilityRetimeResponse(UUID seriesId, int updatedCount) {}
