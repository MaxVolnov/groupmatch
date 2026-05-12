package com.groupmatch.dto.availability;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HeatmapResponse(
        List<HeatmapSlot> slots,
        int granularityMinutes,
        Instant from,
        Instant to
) {
    public record HeatmapSlot(
            Instant startsAt,
            Instant endsAt,
            int count,
            /** null when group.showParticipants == false */
            List<UUID> memberIds,
            List<String> displayNames
    ) {}
}
