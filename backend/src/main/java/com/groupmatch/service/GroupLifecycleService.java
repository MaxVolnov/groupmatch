package com.groupmatch.service;

import com.groupmatch.domain.Group;
import com.groupmatch.domain.GroupRole;
import com.groupmatch.domain.GrpMember;
import com.groupmatch.domain.MemberStatus;
import com.groupmatch.domain.User;
import com.groupmatch.exception.BadRequestException;
import com.groupmatch.exception.GroupNotFoundException;
import com.groupmatch.exception.UserNotFoundException;
import com.groupmatch.repository.GroupRepository;
import com.groupmatch.repository.GrpMemberRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Что происходит с группой, когда меняется состав её участников.
 *
 * Отдельно от {@link GroupService}: там операции над группой по запросу
 * человека («покажи», «переименуй», «добавь участника»), здесь — следствия,
 * которые наступают сами. У этих следствий два независимых источника —
 * обычный выход участника и удаление аккаунта, — и они обязаны вести себя
 * одинаково. Скопированная в два места проверка «а не опустела ли группа»
 * разъехалась бы на первой правке, причём молча: расхождение видно не по
 * ошибке, а по мусорным строкам в базе спустя месяцы.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupLifecycleService {

    private final GroupRepository groupRepository;
    private final GrpMemberRepository grpMemberRepository;
    private final UserRepository userRepository;
    private final GroupAccessGuard groupAccessGuard;

    // ─── Передача владения ────────────────────────────────────────────────────

    /**
     * Передаёт владение другому активному участнику.
     *
     * Прежний владелец остаётся в группе обычным участником: он никуда не
     * уходил, он отдал права.
     */
    @Transactional
    public void transferOwnership(UUID groupId, UUID callerId, UUID newOwnerId) {
        GrpMember currentOwner = groupAccessGuard.requireOwner(groupId, callerId);

        if (callerId.equals(newOwnerId)) {
            throw new BadRequestException("Cannot transfer ownership to yourself");
        }

        GrpMember target = grpMemberRepository.findByGroupAndUser(groupId, newOwnerId)
                .filter(GrpMember::isActive)
                .orElseThrow(() -> new BadRequestException("New owner must be an active member of the group"));

        User newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new UserNotFoundException(newOwnerId));

        // Удалённому аккаунту владение отдавать нельзя: он не сможет войти, и
        // группа окажется с владельцем, которого не существует.
        if (newOwner.isDeleted()) {
            throw new BadRequestException("New owner must be an active member of the group");
        }

        promote(groupId, currentOwner, target, newOwner);
        log.info("Group ownership transferred. groupId={}, from={}, to={}", groupId, callerId, newOwnerId);
    }

    /**
     * Смена владельца в базе.
     *
     * Порядок обязателен. На {@code grp_member} висит частичный уникальный
     * индекс {@code idx_one_owner_per_group} (grp_id, где role='OWNER' и
     * status='ACTIVE'), он не отложенный. Если позволить JPA слить оба
     * изменения одним flush, порядок SQL определяет она, и половину случаев
     * получаем нарушение уникальности. Поэтому сначала снимаем старого
     * владельца и сбрасываем в базу, и только потом ставим нового.
     */
    private void promote(UUID groupId, GrpMember currentOwner, GrpMember target, User newOwner) {
        currentOwner.setRole(GroupRole.MEMBER);
        grpMemberRepository.saveAndFlush(currentOwner);

        target.setRole(GroupRole.OWNER);
        grpMemberRepository.saveAndFlush(target);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        group.setOwner(newOwner);
        groupRepository.save(group);
    }

    // ─── Уход участника ───────────────────────────────────────────────────────

    /**
     * Единственное место, где решается судьба группы после ухода участника.
     * Вызывать после того, как статус ушедшего уже сохранён.
     *
     * @return {@code true}, если группа была удалена
     */
    @Transactional
    public boolean deleteIfEmpty(UUID groupId) {
        long active = grpMemberRepository.countByGroupAndStatus(groupId, MemberStatus.ACTIVE);
        if (active > 0) return false;

        // Слоты, встречи, приглашения и сами записи участников уносит каскад:
        // все они ссылаются на grp с ON DELETE CASCADE (миграции V3-V6).
        deleteGroupNow(groupId);
        log.info("Group deleted: no active members left. groupId={}", groupId);
        return true;
    }

    /**
     * Убирает человека из всех его групп — при удалении аккаунта.
     *
     * Порядок важен: сначала разбираемся с владением, потом уходим. Наоборот
     * не выйдет — {@code requireOwner} не считает владельцем того, кто уже не
     * активен, и группа осталась бы без хозяина.
     */
    @Transactional
    public void removeFromAllGroups(UUID userId) {
        List<GrpMember> memberships = grpMemberRepository.findByUserAndStatus(userId, MemberStatus.ACTIVE);

        for (GrpMember membership : memberships) {
            if (membership.isOwner()) {
                handOverOrDelete(membership.getGroup(), userId);
            }
        }

        // Перечитываем: часть групп исчезла на предыдущем шаге вместе со
        // своими записями участников (каскад в базе). Без flush в
        // deleteGroupNow этот запрос возвращал бы строки, которых уже нет.
        for (GrpMember membership : grpMemberRepository.findByUserAndStatus(userId, MemberStatus.ACTIVE)) {
            membership.setStatus(MemberStatus.LEFT);
            grpMemberRepository.saveAndFlush(membership);
            deleteIfEmpty(membership.getGroup());
        }
    }

    /**
     * Удаление группы со сбросом в базу немедленно.
     *
     * ⚠️ flush обязателен, и это не перестраховка. {@code deleteById} только
     * ставит DELETE в очередь, а Hibernate сбрасывает очередь перед запросом
     * лишь тогда, когда таблицы пересекаются. {@code grp} и {@code grp_member}
     * он пересекающимися не считает — про каскад на уровне базы он не знает, —
     * поэтому следующий SELECT по {@code grp_member} возвращал строки уже
     * удалённой группы, а попытка их сохранить падала с
     * ObjectOptimisticLockingFailureException «expected row count 1 but was 0».
     * Ровно так это и проявилось: 500 на DELETE /api/v1/me.
     */
    private void deleteGroupNow(UUID groupId) {
        groupRepository.deleteById(groupId);
        groupRepository.flush();
    }

    /**
     * Владелец уходит: либо передаём владение, либо группы больше нет.
     *
     * Наследник — участник с самой ранней датой вступления. Правило нужно
     * прежде всего однозначное: любое «по алфавиту» или «случайный» дало бы
     * разный результат на одинаковых данных. Ранний по времени — ещё и самый
     * осмысленный из однозначных: он дольше всех в группе.
     */
    private void handOverOrDelete(UUID groupId, UUID ownerId) {
        Optional<GrpMember> heir = grpMemberRepository
                .findByGroupAndStatus(groupId, MemberStatus.ACTIVE)
                .stream()
                .filter(m -> !m.getUser().equals(ownerId))
                .min(Comparator.comparing(GrpMember::getJoinedAt)
                        // При совпадении даты до микросекунды нужен второй
                        // признак, иначе выбор зависел бы от порядка строк.
                        .thenComparing(GrpMember::getUser));

        if (heir.isEmpty()) {
            deleteGroupNow(groupId);
            log.info("Group deleted with its owner: no other members. groupId={}, ownerId={}", groupId, ownerId);
            return;
        }

        GrpMember currentOwner = grpMemberRepository.findByGroupAndUser(groupId, ownerId).orElseThrow();
        User newOwner = userRepository.findById(heir.get().getUser())
                .orElseThrow(() -> new UserNotFoundException(heir.get().getUser()));

        promote(groupId, currentOwner, heir.get(), newOwner);
        log.info("Group ownership inherited on account deletion. groupId={}, from={}, to={}",
                groupId, ownerId, newOwner.getId());
    }
}
