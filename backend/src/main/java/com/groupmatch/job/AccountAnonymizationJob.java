package com.groupmatch.job;

import com.groupmatch.domain.User;
import com.groupmatch.repository.AvailabilityRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Обезличивает аккаунты, у которых истекла отсрочка после удаления.
 *
 * По образцу {@code PlanExpiryJob}: расписание в конфиге, чтобы на стенде
 * прогон можно было ускорить, не пересобирая приложение.
 *
 * ЧТО ОСТАЁТСЯ И ПОЧЕМУ. Встречи, которые человек создавал, не удаляются.
 * Встреча принадлежит группе, а не автору: она стоит в календарях остальных
 * участников и уже уехала в их {@code .ics}-подписки. Удалить её значило бы
 * стереть данные людей, которые ничего не просили. Автор у встречи остаётся
 * ссылкой на обезличенную строку — по ней больше нельзя узнать, кто это был.
 *
 * Личная доступность, наоборот, удаляется: «свободен во вторник с 19:00» без
 * человека — не данные, а мусор в тепловой карте.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountAnonymizationJob {

    /**
     * Домен из RFC 2606: зарезервирован и гарантированно ни на что не
     * резолвится. Письмо на такой адрес не уйдёт даже по ошибке.
     */
    private static final String DELETED_EMAIL_DOMAIN = "@deleted.invalid";

    /**
     * Имя вместо настоящего. Это данные, а не элемент интерфейса, поэтому
     * значение одно и не локализуется: локаль читателя не имеет отношения к
     * тому, что записано в строке.
     */
    private static final String DELETED_DISPLAY_NAME = "Удалённый пользователь";

    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.account-deletion.grace-days:30}")
    private int graceDays;

    @Scheduled(cron = "${app.account-deletion.anonymize-cron:0 0 3 * * *}")
    @Transactional
    public void anonymizeExpired() {
        Instant cutoff = Instant.now().minus(graceDays, ChronoUnit.DAYS);
        List<User> expired = userRepository.findByDeletedAtBeforeAndAnonymizedAtIsNull(cutoff);

        if (expired.isEmpty()) {
            log.debug("Account anonymization: nothing to do");
            return;
        }

        for (User user : expired) {
            anonymize(user);
        }

        log.info("Account anonymization: processed {} accounts older than {} days",
                expired.size(), graceDays);
    }

    private void anonymize(User user) {
        UUID userId = user.getId();

        int slotsRemoved = availabilityRepository.deleteByUserId(userId);

        user.setEmail("deleted-" + UUID.randomUUID() + DELETED_EMAIL_DOMAIN);
        user.setDisplayName(DELETED_DISPLAY_NAME);

        // Хеш затирается заменой на хеш случайного значения, а не строкой-
        // заглушкой. Заглушка не является корректным хешем Argon2, и
        // passwordEncoder.matches на ней ведёт себя не так, как на нормальном
        // хеше, — а этот вызов стоит на пути входа, и трогать его поведение
        // ради удалённых аккаунтов не стоит. Исходный хеш при этом исчезает
        // одинаково: подобрать вход к случайному UUID никто не может.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

        user.setAnonymizedAt(Instant.now());
        userRepository.save(user);

        log.info("Account anonymized. userId={}, slotsRemoved={}", userId, slotsRemoved);
    }
}
