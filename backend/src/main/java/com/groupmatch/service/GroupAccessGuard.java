package com.groupmatch.service;

import com.groupmatch.domain.GrpMember;
import com.groupmatch.exception.NotGroupMemberException;
import com.groupmatch.exception.NotGroupOwnerException;
import com.groupmatch.repository.GrpMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Проверки доступа к группе.
 *
 * Обе проверки были продублированы по четырём сервисам: `GroupService`,
 * `MeetingService`, `InviteService` и (в возвращающем варианте)
 * `AvailabilityService`. Реализации совпадали, и это ровно тот случай, когда
 * дублирование опасно: правило «кто имеет право трогать группу» — граница
 * доступа, и появление в ней пятой копии с чуть другим условием (скажем, без
 * проверки `isActive`) обнаружилось бы не тестом, а инцидентом.
 *
 * Оба метода возвращают найденную запись: вызывающему она иногда нужна
 * (`AvailabilityService` смотрит на неё дальше), а иногда нет — результат
 * просто игнорируется.
 */
@Component
@RequiredArgsConstructor
public class GroupAccessGuard {

    private final GrpMemberRepository grpMemberRepository;

    /** Участник группы, не исключённый из неё. */
    public GrpMember requireActiveMember(UUID groupId, UUID callerId) {
        return grpMemberRepository.findByGroupAndUser(groupId, callerId)
                .filter(GrpMember::isActive)
                .orElseThrow(NotGroupMemberException::new);
    }

    /** Владелец группы. Владелец, исключённый из группы, владельцем не считается. */
    public GrpMember requireOwner(UUID groupId, UUID callerId) {
        return grpMemberRepository.findByGroupAndUser(groupId, callerId)
                .filter(m -> m.isOwner() && m.isActive())
                .orElseThrow(NotGroupOwnerException::new);
    }
}
