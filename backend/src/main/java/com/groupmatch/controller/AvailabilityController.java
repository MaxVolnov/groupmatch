package com.groupmatch.controller;

import com.groupmatch.dto.availability.AvailabilityRequest;
import com.groupmatch.dto.availability.AvailabilityResponse;
import com.groupmatch.dto.availability.HeatmapResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.AvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityResponse add(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID groupId,
                                    @Valid @RequestBody AvailabilityRequest req) {
        return availabilityService.addSlot(groupId, principal.getId(), principal.getPlan(), req);
    }

    @GetMapping("/my")
    public List<AvailabilityResponse> getMySlots(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable UUID groupId) {
        return availabilityService.getMySlots(groupId, principal.getId());
    }

    @PutMapping("/{slotId}")
    public AvailabilityResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID groupId,
                                       @PathVariable UUID slotId,
                                       @Valid @RequestBody AvailabilityRequest req) {
        return availabilityService.updateSlot(slotId, groupId, principal.getId(), req);
    }

    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID groupId,
                       @PathVariable UUID slotId) {
        availabilityService.deleteSlot(slotId, groupId, principal.getId());
    }

    @GetMapping("/heatmap")
    public HeatmapResponse heatmap(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer granularityMinutes) {
        return availabilityService.getHeatmap(groupId, principal.getId(), from, to, granularityMinutes);
    }
}
