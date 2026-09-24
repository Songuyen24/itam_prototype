package com.company.itam.workflow.recovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Request to perform Smart Check before recovery. */
public record SmartCheckRequest(
    @NotNull(message = "{VALIDATION_REQUIRED}") List<Long> assetIds,
    @NotNull(message = "{VALIDATION_REQUIRED}") @Positive(message = "{validation.positive}") Long returnerUserId,
    List<Long> allocationIds,
    @NotBlank(message = "{VALIDATION_REQUIRED}") String reason
) {}
