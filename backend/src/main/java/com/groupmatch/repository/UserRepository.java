package com.groupmatch.repository;

import com.groupmatch.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String email, String displayName, Pageable pageable);

    /**
     * Удаляет гостевые аккаунты, неактивные дольше указанного срока.
     * Считается от последней активности, а НЕ от даты создания — иначе
     * ежедневно работающий гость терял все данные по возрасту аккаунта.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.guest = true AND u.lastActivityAt < :cutoff")
    int deleteGuestAccountsInactiveSince(@Param("cutoff") Instant cutoff);

    /** Аккаунты, у которых закончился псевдо-премиум и его пора отработать. */
    List<User> findByTrialExpiresAtBefore(Instant threshold);

    /** Отметка активности. Отдельный UPDATE, чтобы не тащить сущность в память. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.lastActivityAt = :now WHERE u.id = :userId")
    int touchLastActivity(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Аккаунты, у которых истекла отсрочка и пора обезличивать.
     * {@code anonymizedAt IS NULL} обязателен: иначе джоба цепляла бы одни и те
     * же строки каждую ночь до скончания века.
     */
    List<User> findByDeletedAtBeforeAndAnonymizedAtIsNull(Instant threshold);

    /**
     * Постраничный список для админки с фильтром по типу аккаунта.
     *
     * Фильтр на сервере, а не отсевом на клиенте: список постраничный, и
     * клиентский фильтр показывал бы «3 из 20» на странице, где всего 20 из
     * тысячи, — то есть врал бы про количество.
     *
     * Один запрос с дискриминатором вместо четырёх методов: варианты
     * различаются одним предикатом, а поиск по подстроке общий для всех, и
     * четыре почти одинаковых запроса разъехались бы на первой же правке.
     *
     * ⚠️ {@code search} не должен быть {@code null}. Пустая строка означает
     * «без поиска»: {@code LIKE '%%'} совпадает со всем. Именно строка, а не
     * {@code null} с проверкой {@code :search IS NULL} — PostgreSQL не может
     * вывести тип нетипизированного {@code null} внутри {@code CONCAT} и падает
     * с {@code function lower(bytea) does not exist}. Проверено: на 500 в
     * админке это и было.
     *
     * @param filter     all | real | guests | test | deleted
     * @param testSuffix домен тестовых аккаунтов в нижнем регистре, с «@»
     */
    @Query("""
            SELECT u FROM User u
            WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:filter = 'all'
                   OR (:filter = 'guests'  AND u.guest = TRUE)
                   OR (:filter = 'deleted' AND u.deletedAt IS NOT NULL)
                   OR (:filter = 'test'    AND LOWER(u.email) LIKE CONCAT('%', :testSuffix))
                   OR (:filter = 'real'    AND u.guest = FALSE
                                           AND u.deletedAt IS NULL
                                           AND LOWER(u.email) NOT LIKE CONCAT('%', :testSuffix)))
            """)
    Page<User> searchWithFilter(@Param("search") String search,
                                @Param("filter") String filter,
                                @Param("testSuffix") String testSuffix,
                                Pageable pageable);
}