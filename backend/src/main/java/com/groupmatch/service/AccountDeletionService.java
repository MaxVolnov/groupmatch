package com.groupmatch.service;

import com.groupmatch.domain.Group;
import com.groupmatch.domain.GrpMember;
import com.groupmatch.domain.MemberStatus;
import com.groupmatch.domain.User;
import com.groupmatch.dto.auth.AccountDeletionPreviewResponse;
import com.groupmatch.dto.auth.OwnedGroupFate;
import com.groupmatch.exception.BadRequestException;
import com.groupmatch.exception.InvalidCredentialsException;
import com.groupmatch.exception.UserNotFoundException;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.UserRepository;
import com.groupmatch.util.EmailMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Удаление аккаунта и его восстановление.
 *
 * Удаление мягкое: строка остаётся, проставляется {@code deleted_at}. Данные
 * физически исчезают позже, обезличиванием ({@code AccountAnonymizationJob}),
 * и между этими моментами есть отсрочка, в которую администратор может
 * вернуть аккаунт.
 *
 * Почему не DELETE из базы. На {@code app_user} висят каскады почти отовсюду
 * (миграция V12), и физическое удаление унесло бы вместе с человеком встречи,
 * которые он создавал для группы, — то есть данные других людей. Плюс отсрочка:
 * «удалить» по ошибке нажимают, и тридцать дней стоят дёшево.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GrpMemberRepository grpMemberRepository;
    private final GroupLifecycleService groupLifecycleService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.account-deletion.grace-days:30}")
    private int graceDays;

    // ─── Что случится, если нажать «удалить» ──────────────────────────────────

    /**
     * Заранее считает судьбу групп, где человек владелец.
     *
     * Нужно интерфейсу: подтверждение обязано называть последствия конкретно —
     * какая группа перейдёт и кому, какая исчезнет, — а не спрашивать
     * «вы уверены?». Клиент сам этого не вычислит: у него нет состава участников
     * чужих групп.
     */
    @Transactional(readOnly = true)
    public AccountDeletionPreviewResponse previewDeletion(UUID userId) {
        List<OwnedGroupFate> fates = grpMemberRepository
                .findByUserAndStatus(userId, MemberStatus.ACTIVE)
                .stream()
                .filter(GrpMember::isOwner)
                .map(m -> fateOf(m.getGroup(), userId))
                .toList();

        return new AccountDeletionPreviewResponse(fates, graceDays);
    }

    private OwnedGroupFate fateOf(UUID groupId, UUID ownerId) {
        Group group = groupRepository.findById(groupId).orElseThrow();
        String heirName = heirOf(groupId, ownerId).map(this::displayNameOf).orElse(null);
        return new OwnedGroupFate(groupId, group.getTitle(), heirName == null, heirName);
    }

    private String displayNameOf(GrpMember member) {
        return userRepository.findById(member.getUser()).map(User::getDisplayName).orElse("—");
    }

    /** Тот же выбор наследника, что и при удалении: самый ранний по дате вступления. */
    private java.util.Optional<GrpMember> heirOf(UUID groupId, UUID ownerId) {
        return grpMemberRepository.findByGroupAndStatus(groupId, MemberStatus.ACTIVE).stream()
                .filter(m -> !m.getUser().equals(ownerId))
                .min(Comparator.comparing(GrpMember::getJoinedAt).thenComparing(GrpMember::getUser));
    }

    // ─── Удаление ─────────────────────────────────────────────────────────────

    /**
     * Человек удаляет себя.
     *
     * Пароль обязателен: удаление необратимо через тридцать дней, а угнанная
     * сессия иначе стирала бы аккаунт одним запросом. У гостя пароля нет —
     * он его никогда не задавал, — и требовать нечего.
     */
    @Transactional
    public void deleteOwnAccount(UUID userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isGuest()) {
            if (password == null || password.isBlank()) {
                throw new BadRequestException("Password confirmation is required");
            }
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                log.info("Account deletion rejected: wrong password. userId={}", userId);
                throw new InvalidCredentialsException("Invalid email or password");
            }
        }

        softDelete(user, "self");
    }

    /** Администратор удаляет чужой аккаунт. Пароля он не знает и знать не должен. */
    @Transactional
    public void deleteByAdmin(UUID targetId) {
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));
        softDelete(user, "admin");
    }

    private void softDelete(User user, String initiator) {
        if (user.isDeleted()) {
            // Повторное удаление не ошибка и не должно сдвигать отсрочку:
            // иначе администратор, нажавший дважды, продлил бы срок хранения.
            log.info("Account already deleted, nothing to do. userId={}", user.getId());
            return;
        }

        user.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(user);

        // Владение передаётся до выхода из групп: requireOwner не считает
        // владельцем того, кто уже неактивен.
        groupLifecycleService.removeFromAllGroups(user.getId());

        // Активные сессии прекращаются сразу. Без этого выданный access-токен
        // жил бы ещё до пятнадцати минут, а refresh — две недели.
        refreshTokenService.invalidateAllSessions(user.getId());
        // Отдельно от refresh-токенов: access-токен — самодостаточный JWT, из
        // Redis его не выкинуть, поэтому метка ставится на пользователя.
        refreshTokenService.blockAccessTokens(user.getId());

        log.info("Account deleted. userId={}, email={}, initiator={}, graceDays={}",
                user.getId(), EmailMasker.mask(user.getEmail()), initiator, graceDays);
    }

    // ─── Восстановление ───────────────────────────────────────────────────────

    /**
     * Возвращает аккаунт в пределах отсрочки.
     *
     * ⚠️ Возвращается только возможность входа. Состав групп не
     * восстанавливается: владение уже перешло другому человеку, а группы без
     * участников удалены физически — отменить это нечем. Администратор должен
     * знать об этом до нажатия, поэтому то же самое написано в интерфейсе.
     */
    @Transactional
    public void restore(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isDeleted()) {
            throw new BadRequestException("Account is not deleted");
        }
        if (user.getAnonymizedAt() != null) {
            throw new BadRequestException("Account is anonymized and cannot be restored");
        }

        user.setDeletedAt(null);
        userRepository.save(user);
        // Снять метку обязательно: она заведена на человека, и без этого не
        // сработал бы даже свежий токен, выданный после восстановления.
        refreshTokenService.unblockAccessTokens(userId);
        log.info("Account restored. userId={}", userId);
    }
}
