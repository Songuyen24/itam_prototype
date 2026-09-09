package com.company.itam.workflow.core.dto;

import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.enums.TransactionType;

import java.time.Instant;

public record TransactionSummaryResponse(
        Long transactionId,
        String transactionCode,
        TransactionType type,
        TransactionStatus status,
        Instant createdAt,
        boolean documentsEditable,
        String editBlockedReason, long expectedVersion, int submittedRevision, String requesterName, String processedByName, String rejectionReason) {

    public static TransactionSummaryResponse fromEntity(TransactionEntity transaction) {
        return new TransactionSummaryResponse(transaction.getTransactionId(), transaction.getTransactionCode(),
                transaction.getType(), transaction.getStatus(), transaction.getCreatedAt(),
                transaction.getType() == TransactionType.IMPORT && transaction.getStatus() == TransactionStatus.DRAFT,
                transaction.getStatus() == TransactionStatus.DRAFT ? null : "DOCUMENT_LOCKED",
                transaction.getContentVersion(), transaction.getSubmittedRevision(),
                transaction.getRequester().getFullName(), transaction.getProcessedBy()==null?null:transaction.getProcessedBy().getFullName(), transaction.getRejectionReason());
    }
}
