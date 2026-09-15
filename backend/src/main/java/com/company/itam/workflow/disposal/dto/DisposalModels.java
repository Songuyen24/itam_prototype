package com.company.itam.workflow.disposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DisposalModels {
    private DisposalModels() {}

    public record SmartCheckRequest(@NotEmpty List<Long> assetIds) {}
    public record CreateRequest(@NotEmpty List<Long> assetIds, @NotBlank String reason,
            @NotNull LocalDate disposalDate, @NotBlank String expectedFingerprint) {}
    public record RejectRequest(@NotBlank String reason) {}
    public record AssetLine(Long assetId, String assetTag, String name, String category, boolean autoAdded) {}
    public record SmartCheckResponse(List<AssetLine> assets, List<String> warnings, String fingerprint) {}
    public record DisposalResponse(Long transactionId, String transactionCode, String status, String reason,
            LocalDate disposalDate, Instant createdAt, List<AssetLine> assets, List<String> warnings, String fingerprint) {}
}
