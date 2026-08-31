package com.groupmatch.service;

import com.groupmatch.domain.*;
import com.groupmatch.dto.invite.CreateInviteRequest;
import com.groupmatch.dto.invite.InvitePreviewResponse;
import com.groupmatch.dto.invite.InviteResponse;
import com.groupmatch.exception.*;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.InviteRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private static final int TOKEN_BYTES = 24; // 48 hex chars

    /**
     * Предел длины имени: {@code @Size(max = 50)} в GuestRequest и
     * SignupRequest, {@code CHECK (char_length(display_name) BETWEEN 2 AND 50)}
     * в миграции V1, {@code maxLength={50}} в форме. Более длинного имени в
     * базе быть не может.
     */
    private static final int MAX_DISPLAY_NAME_LENGTH = 50;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteRepository inviteRepository;
    private final GroupAccessGuard groupAccessGuard;
    private final GrpMemberRepository grpMemberRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final NotificationService notificationService;
    private final NotificationPreferencesService notificationPreferencesService;
    private final EmailService emailService;

    @Value("${app.features.monetization-enabled}")
    private boolean monetizationEnabled;

    @Transactional
    public InviteResponse createInvite(UUID groupId, UUID callerId, Plan callerPlan,
                                       CreateInviteRequest req) {
        groupAccessGuard.requireOwner(groupId, callerId);

        // Rate-limit: invitesPerHour per plan.
        // Тарифное ограничение, а не защита от абьюза, поэтому — под тем же
        // флагом монетизации, что и остальные платные лимиты.
        if (monetizationEnabled) {
            int limit = callerPlan.limits().invitesPerHour();
            if (limit != Integer.MAX_VALUE) {
                Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
                long recentCount = inviteRepository
                        .countByGroupIdAndCreatedByAndCreatedAtAfter(groupId, callerId, oneHourAgo);
                if (recentCount >= limit) {
                    throw new PlanLimitExceededException(
                            "Rate limit: max " + limit + " invites per hour for " + callerPlan + " plan");
                }
            }
        }

        Instant expiresAt = req.expiresAt() != null
                ? req.expiresAt()
                : Instant.now().plus(30, ChronoUnit.DAYS);

        Invite invite = new Invite();
        invite.setGroupId(groupId);
        invite.setToken(generateToken());
        invite.setCreatedBy(callerId);
        invite.setExpiresAt(expiresAt);
        invite.setMaxUses(req.maxUses());

        return toResponse(inviteRepository.save(invite));
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listInvites(UUID groupId, UUID callerId) {
        groupAccessGuard.requireOwner(groupId, callerId);
        return inviteRepository.findByGroupIdAndRevokedFalse(groupId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void revokeInvite(UUID inviteId, UUID groupId, UUID callerId) {
        groupAccessGuard.requireOwner(groupId, callerId);
        Invite invite = inviteRepository.findById(inviteId)
                .filter(i -> i.getGroupId().equals(groupId))
                .orElseThrow(() -> new InviteNotFoundException(inviteId));
        invite.setRevoked(true);
        inviteRepository.save(invite);
    }

    /**
     * Данные приглашения для публичного экрана: что за группа и кто зовёт.
     *
     * Никогда не бросает исключений на «плохой токен» — возвращает
     * {@code valid = false} с причиной. Так сделано намеренно: этот же ответ
     * питает превью ссылки в мессенджерах, а 404 там ломает превью целиком, и
     * человек видит «ссылка битая» вместо объяснения, что приглашение истекло.
     *
     * Причины различаются по порядку убывания определённости: отозванное
     * приглашение остаётся отозванным, даже если срок ещё не вышел.
     */
    @Transactional(readOnly = true)
    public InvitePreviewResponse previewByToken(String token) {
        Invite invite = inviteRepository.findByToken(token).orElse(null);
        if (invite == null) {
            return InvitePreviewResponse.invalid(InvitePreviewResponse.Reason.NOT_FOUND);
        }
        if (invite.isRevoked()) {
            return InvitePreviewResponse.invalid(InvitePreviewResponse.Reason.REVOKED);
        }
        if (!Instant.now().isBefore(invite.getExpiresAt())) {
            return InvitePreviewResponse.invalid(InvitePreviewResponse.Reason.EXPIRED);
        }
        if (invite.getMaxUses() != 0 && invite.getCurrentUses() >= invite.getMaxUses()) {
            return InvitePreviewResponse.invalid(InvitePreviewResponse.Reason.MAX_USES);
        }

        String groupName = groupRepository.findById(invite.getGroupId())
                .map(Group::getTitle)
                .orElse(null);
        if (groupName == null) {
            // Группу удалили, а приглашение осталось. Для человека это ровно то
            // же самое, что несуществующая ссылка.
            return InvitePreviewResponse.invalid(InvitePreviewResponse.Reason.NOT_FOUND);
        }

        String inviterName = userRepository.findById(invite.getCreatedBy())
                .map(User::getDisplayName)
                .orElse(null);

        return InvitePreviewResponse.valid(groupName, inviterName);
    }

    /**
     * Занято ли уже такое имя в группе, куда ведёт приглашение.
     *
     * Нужно ровно для одной подсказки: человек, однажды зашедший гостем из
     * встроенного браузера мессенджера, приходит второй раз с телефона, вводит
     * то же имя и заводит второй аккаунт, не понимая, почему в группе он теперь
     * дважды. Предупредить об этом можно, только сверив имя.
     *
     * <h4>Почему публичный эндпоинт здесь приемлем</h4>
     *
     * Обратиться сюда можно только с валидным токеном приглашения. А обладатель
     * такого токена и без нас может вступить в группу и запросить
     * {@code GET /api/v1/groups/{id}/members} — полный список участников с
     * именами.
     *
     * {@code showParticipants} этому не мешает: флаг ограничивает выдачу
     * теплокарты — в {@code AvailabilityService} при выключенном флаге имена и
     * идентификаторы в слотах зануляются на сервере, а не прячутся во
     * фронтенде, — но на список участников группы он не распространяется вовсе.
     *
     * То есть здесь не раскрывается ничего, чего не даёт сама ссылка: разница
     * ровно в одном — владелец группы не получает уведомления о вступлении.
     *
     * ⚠️ Отсюда следует ограничение на будущее. Рассуждение держится на том,
     * что ответ не превышает того, что и так доступно по ссылке. Любое
     * расширение — вернуть список имён, счётчик участников, что угодно сверх
     * одного бита — обнуляет этот аргумент, и вопрос придётся решать заново.
     *
     * Длина входа ограничена {@link #MAX_DISPLAY_NAME_LENGTH}: имя длиннее
     * всё равно не могло быть сохранено, значит совпасть ни с кем не может, и
     * ходить за этим в базу незачем.
     */
    @Transactional(readOnly = true)
    public boolean isNameTakenInInvitedGroup(String token, String displayName) {
        if (displayName == null || displayName.isBlank()) return false;

        String needle = displayName.trim();
        if (needle.length() > MAX_DISPLAY_NAME_LENGTH) return false;

        Invite invite = inviteRepository.findByToken(token).orElse(null);
        if (invite == null || !invite.isValid()) return false;

        List<UUID> userIds = grpMemberRepository
                .findByGroupAndStatus(invite.getGroupId(), MemberStatus.ACTIVE)
                .stream()
                .map(GrpMember::getUser)
                .toList();
        if (userIds.isEmpty()) return false;

        // Один запрос на всех, как в GroupService.getMembers. Обращение к
        // репозиторию в цикле здесь особенно неуместно: эндпоинт публичный, и
        // число запросов к базе на один HTTP-вызов росло бы с размером группы.
        return userRepository.findAllById(userIds).stream()
                .map(User::getDisplayName)
                .anyMatch(name -> name != null && name.equalsIgnoreCase(needle));
    }

    @Transactional
    public InviteResponse joinByToken(String token, UUID callerId, Plan callerPlan) {
        Invite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new InviteNotFoundException(token));

        if (!invite.isValid()) {
            throw new InviteInvalidException();
        }

        UUID groupId = invite.getGroupId();

        Optional<GrpMember> existingMember = grpMemberRepository.findByGroupAndUser(groupId, callerId);
        if (existingMember.isPresent()) {
            MemberStatus status = existingMember.get().getStatus();
            if (status == MemberStatus.BANNED) throw new MemberBannedException();
            // Already active — idempotent join: redirect to the group, no error shown.
            if (status == MemberStatus.ACTIVE) return toResponse(invite);
        }

        GrpMember ownerMembership = grpMemberRepository
                .findByGroupAndStatus(groupId, MemberStatus.ACTIVE)
                .stream().filter(GrpMember::isOwner).findFirst()
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // Лимит участников считается по плану ВЛАДЕЛЬЦА (его тариф определяет
        // вместимость группы), а не по плану входящего. Раньше проверки здесь не
        // было вовсе: лимит из GroupService.addMember обходился ссылкой-инвайтом.
        // Под тем же флагом монетизации, что и остальные платные лимиты.
        if (monetizationEnabled) {
            Plan ownerPlan = userRepository.findById(ownerMembership.getUser())
                    .map(User::getPlan)
                    .orElse(Plan.FREE);
            long current = grpMemberRepository.countByGroupAndStatus(groupId, MemberStatus.ACTIVE);
            int maxMembers = ownerPlan.limits().maxMembersPerGroup();
            if (current >= maxMembers) {
                throw new PlanLimitExceededException(
                        "Plan limit reached: max " + maxMembers + " members per group for " + ownerPlan + " plan");
            }
        }

        // Reuse existing LEFT member record if present, otherwise create new
        GrpMember member = existingMember
                .orElse(new GrpMember(groupId, callerId, GroupRole.MEMBER, MemberStatus.ACTIVE));
        member.setStatus(MemberStatus.ACTIVE);
        grpMemberRepository.save(member);

        invite.setCurrentUses(invite.getCurrentUses() + 1);
        inviteRepository.save(invite);

        UUID ownerId = ownerMembership.getUser();
        if (!ownerId.equals(callerId)) {
            User joiner = userRepository.findById(callerId).orElse(null);
            User owner  = userRepository.findById(ownerId).orElse(null);
            Group group = groupRepository.findById(groupId).orElse(null);
            if (joiner != null && owner != null && group != null) {
                NotificationPreferences ownerPrefs = notificationPreferencesService.getOrCreate(ownerId);
                if (ownerPrefs.isInappMemberJoined()) {
                    notificationService.create(ownerId, NotificationType.MEMBER_JOINED, Map.of(
                        "groupId", groupId.toString(),
                        "groupTitle", group.getTitle(),
                        "joinerId", callerId.toString(),
                        "joinerName", joiner.getDisplayName()
                    ));
                }
                if (ownerPrefs.isEmailMemberJoined() && !owner.isGuest()) {
                    try {
                        emailService.sendMemberJoinedEmail(
                            owner.getEmail(),
                            owner.getLocale(),
                            joiner.getDisplayName(),
                            group.getTitle()
                        );
                    } catch (Exception e) {
                        log.warn("Failed to send member joined email to ownerId={}. error={}", ownerId, e.getMessage());
                    }
                }
            }
        }

        return toResponse(invite);
    }

    // --- helpers ---

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private InviteResponse toResponse(Invite i) {
        return new InviteResponse(i.getId(), i.getGroupId(), i.getToken(), i.getCreatedBy(),
                i.getCreatedAt(), i.getExpiresAt(), i.getMaxUses(), i.getCurrentUses(), i.isRevoked());
    }
}
