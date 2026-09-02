package com.groupmatch.controller;

import com.groupmatch.dto.availability.AvailabilityResponse;
import com.groupmatch.dto.availability.AvailabilityRetimeResponse;
import com.groupmatch.dto.availability.AvailabilityTimeRequest;
import com.groupmatch.dto.availability.DeleteScope;
import jakarta.validation.Valid;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Слот по его собственному идентификатору, без группы в пути.
 *
 * Отдельный контроллер, потому что остальные операции с доступностью живут
 * под {@code /groups/{groupId}/availability}, а этой группа не нужна: она
 * выводится из самого слота. Существующее удаление одиночного слота под
 * прежним адресом остаётся нетронутым — новый путь его не заменяет и не
 * повторяет, он про область действия.
 */
@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class AvailabilitySlotController {

    private final AvailabilityService availabilityService;

    /**
     * Правка времени одного слота. Слот при этом выпадает из своей серии —
     * это видно по пустому {@code seriesId} в ответе.
     */
    @PatchMapping("/{slotId}")
    public AvailabilityResponse retime(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID slotId,
                                       @Valid @RequestBody AvailabilityTimeRequest req) {
        return availabilityService.retimeSlot(slotId, principal.getId(), req);
    }

    /**
     * Правка времени всей серии слота. Даты остаются свои: вторники остаются
     * вторниками. У слота без серии равносильно правке одного слота.
     */
    @PatchMapping("/{slotId}/series")
    public AvailabilityRetimeResponse retimeSeries(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable UUID slotId,
                                                   @Valid @RequestBody AvailabilityTimeRequest req) {
        return availabilityService.retimeSeries(slotId, principal.getId(), req);
    }

    /**
     * @param scope {@code single} (по умолчанию) — только этот слот;
     *              {@code series} — все слоты его серии. У одиночного слота
     *              {@code series} равносилен {@code single}.
     */
    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID slotId,
                       @RequestParam(required = false) String scope) {
        availabilityService.deleteSlot(slotId, principal.getId(), DeleteScope.parse(scope));
    }
}
