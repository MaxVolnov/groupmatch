package com.groupmatch.dto.auth;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VerifyEmailRequest(@NotNull UUID token) {}
