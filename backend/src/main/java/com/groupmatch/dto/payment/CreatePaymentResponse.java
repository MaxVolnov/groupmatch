package com.groupmatch.dto.payment;

import java.util.UUID;

public record CreatePaymentResponse(
    UUID subscriptionId,
    String confirmationUrl,
    long amountKopecks,
    String currency
) {}
