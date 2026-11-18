package com.company.itam.workflow.core.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class WorkflowProgressModels {
    private WorkflowProgressModels() {}

    public record Group(
            String groupId,
            String status,
            List<Long> assetIds,
            List<Long> allocationIds,
            String reason,
            Instant approvedAt,
            LocalDate dueDate,
            LocalDate executionDate,
            Long documentId) {
        public Group(
                String groupId,
                String status,
                List<Long> assetIds,
                List<Long> allocationIds,
                String reason,
                Instant approvedAt,
                LocalDate dueDate) {
            this(groupId, status, assetIds, allocationIds, reason, approvedAt, dueDate, null, null);
        }
    }

    public record Action(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotEmpty @Size(max = 10100) List<@NotBlank String> groupIds,
            @Size(max = 4000) String reason) {}
}
