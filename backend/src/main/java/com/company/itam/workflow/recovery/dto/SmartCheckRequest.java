package com.company.itam.workflow.recovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Request to perform Smart Check before recovery. */
public record SmartCheckRequest(
    @NotEmpty(message = "{VALIDATION_REQUIRED}") List<Long> assetIds,
    @NotBlank(message = "{VALIDATION_REQUIRED}") String reason
) {}
