package com.company.itam.workflow.recovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request to complete a recovery transaction. */
public record RecoveryRequest(
    @NotNull(message = "{VALIDATION_REQUIRED}") Long returnerUserId,
    @NotNull(message = "{VALIDATION_REQUIRED}") Long receivingLocationId,
    @NotNull(message = "{VALIDATION_REQUIRED}") LocalDate recoveryDate,
    @NotBlank(message = "{VALIDATION_REQUIRED}") @Size(max = 4000, message = "{VALIDATION_MAX_LENGTH}") String reason,
    @NotEmpty(message = "{VALIDATION_REQUIRED}") List<Long> assetIds,
    /** assetId -> {componentAction: "KEEP_ATTACHED"|"DETACH", recoverPerUser: boolean} */
    Map<Long, ComponentActions> componentActions,
    /** allocationId -> whether to recover this Per-User allocation */
    Map<Long, Boolean> perUserActions,
    String expectedFingerprint
) {
    public record ComponentActions(String componentAction, boolean recoverPerUser) {}
}
