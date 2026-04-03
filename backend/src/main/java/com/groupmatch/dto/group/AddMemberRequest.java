package com.groupmatch.dto.group;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMemberRequest(@NotNull UUID userId) {}
