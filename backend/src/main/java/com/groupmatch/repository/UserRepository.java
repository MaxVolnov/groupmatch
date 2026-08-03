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
}