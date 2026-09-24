package com.company.itam.workflow.recovery.dto;

import java.util.List;

/** Response from Smart Check showing required/optional/blocked assets and component decisions. */
public record SmartCheckResponse(
    List<RequiredAsset> requiredAssets,
    List<OptionalAsset> optionalAssets,
    List<BlockedAsset> blockedAssets,
    List<ComponentDecision> componentDecisions,
    List<String> warnings,
    String fingerprint
) {
    public record RequiredAsset(Long assetId, String assetTag, String name, String category, String reason) {}
    public record OptionalAsset(Long allocationId, Long assetId, String assetTag, String name, int seats, 
        Long userId, String userName, Long deviceId, String deviceTag, boolean selected) {}
    public record BlockedAsset(Long assetId, String assetTag, String name, String category, String reason) {}
    public record ComponentDecision(Long assetId, String assetTag, String name, 
        List<DecisionOption> options, String defaultAction) {}
    public record DecisionOption(String action, String label, String description) {}
}
