package com.company.itam.publication.dto;

import java.time.Instant;
import java.util.List;

public record PublicationStatusResponse(
        Long transactionId,
        String transactionCode,
        String transactionType,
        PdfStatus pdf,
        List<EmailAttempt> emails) {
    public record PdfStatus(Long documentId, String fileName, Integer version, String templateVersion,
                            Instant issuedAt, String status, String errorMessage) {}
    public record EmailAttempt(Long emailLogId, String recipient, String eventType, String status,
                               String errorMessage, Instant sentAt, Instant createdAt) {}
}
