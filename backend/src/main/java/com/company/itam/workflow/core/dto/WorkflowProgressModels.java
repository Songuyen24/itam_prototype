package com.company.itam.workflow.core.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkflowProgressModels {
    private WorkflowProgressModels() {}
    public record Group(String groupId, String status, List<Long> assetIds, List<Long> allocationIds,
                        String reason, Instant approvedAt, LocalDate dueDate, LocalDate executionDate, Long documentId) {
        public Group(String groupId,String status,List<Long> assetIds,List<Long> allocationIds,String reason,Instant approvedAt,LocalDate dueDate) {
            this(groupId,status,assetIds,allocationIds,reason,approvedAt,dueDate,null,null);
        }
    }
    public record Action(@NotNull @jakarta.validation.constraints.PositiveOrZero Long expectedVersion,
                         @NotEmpty @jakarta.validation.constraints.Size(max=10100) List<@jakarta.validation.constraints.NotBlank String> groupIds,
                         @jakarta.validation.constraints.Size(max=4000) String reason) {}
}
