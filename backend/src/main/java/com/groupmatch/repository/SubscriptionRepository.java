package com.groupmatch.repository;

import com.groupmatch.domain.Subscription;
import com.groupmatch.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByYookassaPaymentId(String yookassaPaymentId);
    Optional<Subscription> findTopByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, SubscriptionStatus status);
    List<Subscription> findByStatusAndExpiresAtBefore(SubscriptionStatus status, Instant threshold);
    boolean existsByUserIdAndStatusAndIdNot(UUID userId, SubscriptionStatus status, UUID id);
}
