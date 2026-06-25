package com.groupmatch.dto.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(
    @NotBlank String plan,
    @Min(1) @Max(12) int periodMonths
) {}
