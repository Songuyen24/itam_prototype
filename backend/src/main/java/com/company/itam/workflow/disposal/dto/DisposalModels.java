package com.company.itam.workflow.disposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DisposalModels {
    private DisposalModels() {}

    public record SmartCheckRequest(@NotEmpty List<@NotNull @Positive Long> assetIds) {}
    public record CreateRequest(@NotEmpty List<@NotNull @Positive Long> assetIds, @NotBlank String reason,
            @NotNull LocalDate disposalDate, @NotBlank String expectedFingerprint) {}
    public record ApproveRequest(@NotBlank String expectedFingerprint) {}
    public record PerUserDecision(@NotNull @Positive Long allocationId, @NotNull Boolean release) {}
    public record PerUserRequest(@NotBlank String expectedFingerprint,
            @NotEmpty List<@NotNull @jakarta.validation.Valid PerUserDecision> decisions) {}
    public record RejectRequest(@NotBlank String reason) {}
    public record AssetLine(Long assetId, String assetTag, String name, String category, String serialNumber, boolean autoAdded) {}
    public record OemAllocation(Long allocationId, Long licenseAssetId, String assetTag,
            Long deviceId, String deviceTag, int seats) {}
    public record PerUserLink(Long allocationId, Long licenseAssetId, String assetTag,
            Long deviceId, String deviceTag, String userName, int seats) {}
    public record SmartCheckResponse(List<AssetLine> assets, List<OemAllocation> oemAllocations,
            List<PerUserLink> perUserLinks, List<String> warnings, String fingerprint) {}
    public record DisposalResponse(Long transactionId, String transactionCode, String status, String reason,
            LocalDate disposalDate, Instant createdAt, String actorName, List<AssetLine> assets,
            List<OemAllocation> oemAllocations, List<PerUserLink> perUserLinks,
            List<String> warnings, String fingerprint) {}
}
