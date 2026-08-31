package com.groupmatch.dto.availability;

/**
 * @param deletedCount сколько строк удалено; при {@code dryRun} — сколько
 *                     было бы удалено
 */
public record AvailabilityBulkClearResponse(int deletedCount) {}
