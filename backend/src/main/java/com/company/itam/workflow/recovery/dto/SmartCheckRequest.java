package com.company.itam.workflow.recovery.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request to perform Smart Check before recovery. */
public record SmartCheckRequest(
        @NotNull(message = "{VALIDATION_REQUIRED}") List<Long> assetIds,
        @NotNull(message = "{VALIDATION_REQUIRED}") @Positive(message = "{validation.positive}") Long returnerUserId,
        List<Long> allocationIds,
        @Size(max = 4000, message = "{VALIDATION_MAX_LENGTH}") String reason) {}
