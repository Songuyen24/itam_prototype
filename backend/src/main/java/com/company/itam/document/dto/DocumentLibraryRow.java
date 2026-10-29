package com.company.itam.document.dto;

import com.company.itam.common.enums.DocumentType;
import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.enums.TransactionType;
import java.time.Instant;

public record DocumentLibraryRow(Long documentId, String originalFileName, DocumentType documentType,
        String mimeType, Long fileSize, String uploadedByName, Instant createdAt, boolean locked,
        Long transactionId, String transactionCode, TransactionType transactionType, TransactionStatus transactionStatus) {
    public static DocumentLibraryRow fromEntity(DocumentEntity document) {
        var transaction = document.getTransaction();
        return new DocumentLibraryRow(document.getDocumentId(), document.getOriginalFileName(), document.getDocumentType(),
            document.getMimeType(), document.getFileSize(), document.getUploadedBy().getFullName(), document.getCreatedAt(),
            Boolean.TRUE.equals(document.getLocked()), transaction.getTransactionId(), transaction.getTransactionCode(),
            transaction.getType(), transaction.getStatus());
    }
}
