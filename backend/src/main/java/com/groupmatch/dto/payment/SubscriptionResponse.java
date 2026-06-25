package com.groupmatch.dto.payment;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
    UUID id,
    String plan,
    String status,
    long amountKopecks,
    int periodMonths,
    Instant expiresAt,
    Instant createdAt
) {}
