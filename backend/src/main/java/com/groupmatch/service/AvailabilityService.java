package com.groupmatch.service;

import com.groupmatch.domain.*;
import com.groupmatch.dto.availability.AvailabilityRequest;
import com.groupmatch.dto.availability.AvailabilityResponse;
import com.groupmatch.dto.availability.AvailabilitySeriesRequest;
import com.groupmatch.dto.availability.AvailabilitySeriesResponse;
import com.groupmatch.dto.availability.DeleteScope;
import com.groupmatch.dto.availability.HeatmapResponse;
import com.groupmatch.dto.availability.HeatmapResponse.HeatmapSlot;
import com.groupmatch.exception.*;
import com.groupmatch.repository.AvailabilityRepository;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private static final int DEFAULT_GRANULARITY_MINUTES = 30;

    /**
     * Потолок на одну серию и на её длину в днях.
     *
     * Двести слотов — это «каждый будний день на сорок недель» или «дважды в
     * неделю на два года». Дальше начинается не планирование встреч, а импорт
     * календаря, и делать его этой ручкой не стоит: серия разворачивается в
     * строки, и отменить её можно только удалив все двести.
     */
    private static final int MAX_SERIES_SLOTS = 200;
    private static final int MAX_SERIES_DAYS = 365;

    private final AvailabilityRepository availabilityRepository;
    private final GroupAccessGuard groupAccessGuard;
    private final GrpMemberRepository grpMemberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final HeatmapCacheService heatmapCacheService;

    @Value("${app.features.monetization-enabled}")
    private boolean monetizationEnabled;

    @Transactional
    public AvailabilityResponse addSlot(UUID groupId, UUID callerId, Plan callerPlan,
                                        AvailabilityRequest req) {
        validateSlotTimes(req.startsAt(), req.endsAt());
        GrpMember membership = groupAccessGuard.requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        if (group.isLocked() && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }

        // Лимит слотов — часть платной модели, как лимиты групп и участников
        // в GroupService: при выключенной монетизации не применяется.
        if (monetizationEnabled) {
            long existing = availabilityRepository.countByGroupIdAndUserId(groupId, callerId);
            int maxSlots = callerPlan.limits().maxSlotsPerMember();
            if (existing >= maxSlots) {
                throw new PlanLimitExceededException(
                        "Plan limit reached: max " + maxSlots + " slots per group for " + callerPlan + " plan");
            }
        }

        Availability slot = availabilityRepository.save(
                new Availability(groupId, callerId, req.startsAt(), req.endsAt(), req.note()));
        bumpVersion(group);
        return toResponse(slot);
    }

    /**
     * Разворачивает правило повторения в обычные слоты и сохраняет их одной
     * пачкой под общим {@code seriesId}.
     *
     * Шаг делается по локальной дате в зоне пользователя, а не прибавлением
     * суток к {@link Instant}. Разница видна дважды в год: в неделю перевода
     * часов сутки длятся 23 или 25 часов, и «каждый вторник в 10:00»,
     * посчитанное шагом по абсолютному времени, уезжает на 9:00 или 11:00 —
     * то есть перестаёт быть тем, что человек ввёл.
     */
    @Transactional
    public AvailabilitySeriesResponse createSeries(UUID groupId, UUID callerId, Plan callerPlan,
                                                   AvailabilitySeriesRequest req) {
        ZoneId zone = parseZone(req.timeZone());

        if (req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("endDate must not be before startDate");
        }
        if (req.endDate().isAfter(req.startDate().plusDays(MAX_SERIES_DAYS))) {
            throw new BadRequestException(
                    "Series cannot span more than " + MAX_SERIES_DAYS + " days");
        }
        if (!req.endTime().isAfter(req.startTime())) {
            throw new BadRequestException("endTime must be after startTime");
        }

        List<LocalDate> dates = matchingDates(req);
        if (dates.size() > MAX_SERIES_SLOTS) {
            // Число в сообщении — не украшение: без него человек не понимает,
            // на сколько сузить диапазон, и подбирает его наугад.
            throw new BadRequestException("Series would create " + dates.size()
                    + " slots, maximum is " + MAX_SERIES_SLOTS);
        }
        if (dates.isEmpty()) {
            throw new BadRequestException("No dates match the given daysOfWeek in that range");
        }

        GrpMember membership = groupAccessGuard.requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);
        if (group.isLocked() && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }

        // Тот же лимит плана, что и у одиночного слота, но проверенный на всю
        // пачку сразу: иначе серия сохранялась бы частично и оставляла
        // человека с половиной введённого.
        if (monetizationEnabled) {
            long existing = availabilityRepository.countByGroupIdAndUserId(groupId, callerId);
            int maxSlots = callerPlan.limits().maxSlotsPerMember();
            if (existing + dates.size() > maxSlots) {
                throw new PlanLimitExceededException(
                        "Plan limit reached: max " + maxSlots + " slots per group for " + callerPlan + " plan");
            }
        }

        UUID seriesId = UUID.randomUUID();
        List<Availability> slots = new ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            ZonedDateTime start = ZonedDateTime.of(date, req.startTime(), zone);
            ZonedDateTime end = ZonedDateTime.of(date, req.endTime(), zone);
            // Те же правила, что и у одиночного слота. Без этой проверки серия
            // из слотов короче пяти минут уходила бы в CHECK-констрейнт и
            // возвращалась пользователю пятисоткой.
            validateSlotTimes(start.toInstant(), end.toInstant());
            slots.add(new Availability(groupId, callerId,
                    start.toInstant(), end.toInstant(), null, seriesId));
        }

        availabilityRepository.saveAll(slots);
        bumpVersion(group);
        return new AvailabilitySeriesResponse(seriesId, slots.size());
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getMySlots(UUID groupId, UUID callerId) {
        groupAccessGuard.requireActiveMember(groupId, callerId);
        return availabilityRepository.findByGroupIdAndUserId(groupId, callerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AvailabilityResponse updateSlot(UUID slotId, UUID groupId, UUID callerId,
                                           AvailabilityRequest req) {
        validateSlotTimes(req.startsAt(), req.endsAt());
        GrpMember membership = groupAccessGuard.requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        Availability slot = availabilityRepository.findById(slotId)
                .filter(s -> s.getGroupId().equals(groupId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (!slot.getUserId().equals(callerId)) {
            // Members can only edit their own slots; OWNER can edit any
            if (!membership.isOwner()) {
                throw new NotGroupOwnerException();
            }
        }
        if (group.isLocked() && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }

        slot.setStartsAt(req.startsAt());
        slot.setEndsAt(req.endsAt());
        slot.setNote(req.note());
        bumpVersion(group);
        return toResponse(availabilityRepository.save(slot));
    }

    @Transactional
    public void deleteSlot(UUID slotId, UUID groupId, UUID callerId) {
        GrpMember membership = groupAccessGuard.requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        Availability slot = availabilityRepository.findById(slotId)
                .filter(s -> s.getGroupId().equals(groupId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (!slot.getUserId().equals(callerId) && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }
        if (group.isLocked() && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }

        availabilityRepository.delete(slot);
        bumpVersion(group);
    }

    /**
     * Удаление с областью действия. Группа не в пути, а выводится из самого
     * слота: идентификатор слота уникален глобально, и требовать от клиента
     * ещё и группу значило бы требовать данные, которые он должен где-то
     * держать только ради этого вызова.
     *
     * Права проверяются ровно те же, что в {@link #deleteSlot}: свой слот
     * можно всегда, чужой — только владельцу группы, в запертой группе —
     * только владельцу.
     *
     * @return сколько строк удалено
     */
    @Transactional
    public int deleteSlot(UUID slotId, UUID callerId, DeleteScope scope) {
        Availability slot = availabilityRepository.findById(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        UUID groupId = slot.getGroupId();
        GrpMember membership = groupAccessGuard.requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        if (!slot.getUserId().equals(callerId) && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }
        if (group.isLocked() && !membership.isOwner()) {
            throw new NotGroupOwnerException();
        }

        int deleted;
        // У одиночного слота seriesId равен null, и scope=series для него —
        // не ошибка, а то же самое, что single. Клиент не обязан знать заранее,
        // серийный слот он удаляет или нет.
        if (scope == DeleteScope.SERIES && slot.getSeriesId() != null) {
            // Владелец слота, а не вызывающий: владелец группы может удалить
            // чужую серию, и удалить он должен именно её, а не пересечение с
            // собственными слотами.
            deleted = availabilityRepository.deleteBySeriesIdAndUserId(
                    slot.getSeriesId(), slot.getUserId());
        } else {
            availabilityRepository.delete(slot);
            deleted = 1;
        }

        bumpVersion(group);
        return deleted;
    }

    @Transactional(readOnly = true)
    public HeatmapResponse getHeatmap(UUID groupId, UUID callerId,
                                      Instant from, Instant to, Integer granularityMinutes) {
        groupAccessGuard.requireActiveMember(groupId, callerId);
        Group group = loadGroup(groupId);

        int granularity = (granularityMinutes != null && granularityMinutes > 0)
                ? granularityMinutes : DEFAULT_GRANULARITY_MINUTES;
        if (from == null) from = Instant.now().truncatedTo(ChronoUnit.DAYS);
        if (to == null) to = from.plus(7L, ChronoUnit.DAYS);

        Optional<HeatmapResponse> cached = heatmapCacheService.get(groupId, group.getVersion(), from, to, granularity);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<Availability> slots = availabilityRepository
                .findByGroupIdAndStartsAtLessThanAndEndsAtGreaterThan(groupId, to, from);

        Map<UUID, String> userNames = Collections.emptyMap();
        if (group.isShowParticipants() && !slots.isEmpty()) {
            Set<UUID> userIds = slots.stream().map(Availability::getUserId).collect(Collectors.toSet());
            userNames = userRepository.findAllById(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getDisplayName));
        }

        List<HeatmapSlot> heatmapSlots = computeBuckets(slots, from, to, granularity,
                group.isShowParticipants(), userNames);

        HeatmapResponse response = new HeatmapResponse(heatmapSlots, granularity, from, to);
        heatmapCacheService.put(groupId, group.getVersion(), from, to, granularity, response);
        return response;
    }

    // --- private helpers ---

    private List<HeatmapSlot> computeBuckets(List<Availability> slots,
                                              Instant from, Instant to,
                                              int granularityMinutes,
                                              boolean showParticipants,
                                              Map<UUID, String> userNames) {
        List<HeatmapSlot> result = new ArrayList<>();
        Instant bucketStart = from;
        long bucketSize = (long) granularityMinutes * 60;

        while (bucketStart.isBefore(to)) {
            Instant bucketEnd = bucketStart.plusSeconds(bucketSize);
            if (bucketEnd.isAfter(to)) bucketEnd = to;

            final Instant bs = bucketStart;
            final Instant be = bucketEnd;

            // Users available in this bucket (slot overlaps bucket)
            List<UUID> available = slots.stream()
                    .filter(s -> s.getStartsAt().isBefore(be) && s.getEndsAt().isAfter(bs))
                    .map(Availability::getUserId)
                    .distinct()
                    .toList();

            if (!available.isEmpty()) {
                List<UUID> memberIds = showParticipants ? available : null;
                List<String> displayNames = showParticipants
                        ? available.stream().map(id -> userNames.getOrDefault(id, id.toString())).toList()
                        : null;
                result.add(new HeatmapSlot(bs, be, available.size(), memberIds, displayNames));
            }

            bucketStart = bucketEnd;
        }

        return result;
    }

    private Group loadGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
    }

    private void bumpVersion(Group group) {
        group.setVersion(group.getVersion() + 1);
        groupRepository.save(group);
    }

    /**
     * Зона — строго IANA («Europe/Moscow»), не смещение.
     *
     * {@link ZoneId#of} проглотил бы и «UTC+3», и «+03:00», и это было бы
     * хуже отказа: фиксированное смещение не знает про перевод часов, а весь
     * смысл разворачивания серии по локальному времени — в том, что перевод
     * оно переживает. Поэтому проверяем по списку зон, а не по разбору строки.
     */
    private ZoneId parseZone(String timeZone) {
        if (timeZone == null || !ZoneId.getAvailableZoneIds().contains(timeZone)) {
            throw new BadRequestException("Unknown IANA time zone: " + timeZone);
        }
        return ZoneId.of(timeZone);
    }

    /** Даты диапазона, чей день недели входит в правило. Шаг — календарный день. */
    private List<LocalDate> matchingDates(AvailabilitySeriesRequest req) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = req.startDate(); !date.isAfter(req.endDate()); date = date.plusDays(1)) {
            if (req.daysOfWeek().contains(date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        return dates;
    }

    private void validateSlotTimes(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("ends_at must be after starts_at");
        }
        long minutes = ChronoUnit.MINUTES.between(startsAt, endsAt);
        if (minutes < 5) {
            throw new IllegalArgumentException("Slot must be at least 5 minutes");
        }
        if (minutes > 48 * 60) {
            throw new IllegalArgumentException("Slot cannot exceed 48 hours");
        }
    }

    private AvailabilityResponse toResponse(Availability s) {
        return new AvailabilityResponse(s.getId(), s.getGroupId(), s.getUserId(),
                s.getStartsAt(), s.getEndsAt(), s.getNote(), s.getSeriesId(), s.getCreatedAt());
    }
}
