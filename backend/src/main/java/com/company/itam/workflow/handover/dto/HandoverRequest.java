package com.company.itam.workflow.handover.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record HandoverRequest(
        @NotNull(message="{validation.required}") @Positive(message="{validation.positive}") Long recipientUserId,
        @NotNull(message="{validation.required}") @Positive(message="{validation.positive}") Long destinationLocationId,
        @NotNull(message="{validation.required}") LocalDate handoverDate,
        @NotNull(message="{validation.required}") @Size(max=100, message="{validation.listSize}") List<@NotNull(message="{validation.required}") @Positive(message="{validation.positive}") Long> assetIds,
        @NotNull(message="{validation.required}") @Size(max=100, message="{validation.listSize}") List<@NotNull(message="{validation.required}") @Valid LicenseLine> licenses,
        @Size(max=4000, message="{validation.size}") String notes,
        @Size(max=64, message="{validation.size}") String expectedFingerprint) {
    public record LicenseLine(@NotNull(message="{validation.required}") @Positive(message="{validation.positive}") Long assetId, @Min(value=1, message="{validation.minimum}") @Max(value=100, message="{validation.maximum}") int seats, @Positive(message="{validation.positive}") Long deviceId) {}
}
