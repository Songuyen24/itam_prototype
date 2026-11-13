package com.company.itam.workflow.recovery.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.company.itam.workflow.core.dto.WorkflowProgressModels.Group;

/** Response after completing a recovery transaction. */
public record RecoveryResponse(
    Long transactionId,
    String transactionCode,
    Instant completedAt,
    Long returnerUserId,
    String returnerName,
    String returnerEmail,
    Long receivingLocationId,
    String receivingLocationName,
    LocalDate recoveryDate,
    String reason,
    String fingerprint,
    List<RecoveryLine> lines,
    String status,
    Long creatorUserId,
    String creatorName,
    RecoveryRequest request,
    long contentVersion,
    List<Group> groups,
    List<RecoveryLine> proposalLines
) {
    public RecoveryResponse(Long transactionId, String transactionCode, Instant completedAt,
            Long returnerUserId, String returnerName, String returnerEmail, Long receivingLocationId,
            String receivingLocationName, LocalDate recoveryDate, String reason, String fingerprint, List<RecoveryLine> lines) {
        this(transactionId,transactionCode,completedAt,returnerUserId,returnerName,returnerEmail,
            receivingLocationId,receivingLocationName,recoveryDate,reason,fingerprint,lines,null,null,null,null,0,List.of(),List.of());
    }
    public record RecoveryLine(
        Long assetId,
        String assetTag,
        String name,
        String category,
        Long parentAssetId,
        int seats,
        List<AllocationResult> allocations,
        java.util.Map<String, Object> details
    ) {}
    public record AllocationResult(Long allocationId, int seats, String action, String assignmentType) {}
}
