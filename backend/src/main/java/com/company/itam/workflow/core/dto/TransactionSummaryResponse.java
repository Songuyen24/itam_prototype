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
        String editBlockedReason) {

    public static TransactionSummaryResponse fromEntity(TransactionEntity transaction) {
        return new TransactionSummaryResponse(transaction.getTransactionId(), transaction.getTransactionCode(),
                transaction.getType(), transaction.getStatus(), transaction.getCreatedAt(),
                false, "DOCUMENT_WORKFLOW_NOT_READY");
    }
}
