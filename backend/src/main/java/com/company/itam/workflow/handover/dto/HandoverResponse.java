package com.company.itam.workflow.handover.dto;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record HandoverResponse(Long transactionId, String transactionCode, Instant completedAt,
        Long recipientUserId, String recipientName, String recipientEmail,
        Long destinationLocationId, String destinationLocationName, LocalDate handoverDate,
        String notes, String fingerprint, List<Line> lines) {
    public record Allocation(Long allocationId, Long deviceId, int seats, String assignmentType, String deviceTag, String deviceName) {}
    public record Line(Long assetId, String assetTag, String name, String category, Long parentAssetId,
            int seats, List<Allocation> allocations, Map<String,Object> details) {}
}
