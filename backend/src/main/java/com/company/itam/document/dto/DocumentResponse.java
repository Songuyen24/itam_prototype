package com.company.itam.document.dto;

import com.company.itam.common.enums.DocumentType;
import com.company.itam.document.entity.DocumentEntity;

import java.time.Instant;

public record DocumentResponse(
        Long documentId,
        Long transactionId,
        Long assetId,
        DocumentType documentType,
        String originalFileName,
        String mimeType,
        Long fileSize,
        String uploadedByName,
        Instant createdAt,
        boolean locked) {

    public static DocumentResponse fromEntity(DocumentEntity document) {
        return new DocumentResponse(
                document.getDocumentId(),
                document.getTransaction().getTransactionId(),
                document.getAsset() == null ? null : document.getAsset().getAssetId(),
                document.getDocumentType(),
                document.getOriginalFileName(),
                document.getMimeType(),
                document.getFileSize(),
                document.getUploadedBy().getFullName(),
                document.getCreatedAt(),
                Boolean.TRUE.equals(document.getLocked()));
    }
}
