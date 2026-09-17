package com.company.itam.publication.service;

import com.company.itam.common.enums.DocumentType;
import com.company.itam.common.enums.EmailStatus;
import com.company.itam.common.exception.AppException;
import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.document.dto.DocumentDownload;
import com.company.itam.document.dto.DocumentResponse;
import com.company.itam.document.repository.DocumentRepository;
import com.company.itam.document.service.DocumentAccessService;
import com.company.itam.document.service.LocalDocumentStorage;
import com.company.itam.notification.entity.EmailLogEntity;
import com.company.itam.notification.repository.EmailLogRepository;
import com.company.itam.publication.dto.PublicationStatusResponse;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.enums.TransactionType;
import com.company.itam.workflow.core.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TransactionPublicationService {
    private static final Logger log = LoggerFactory.getLogger(TransactionPublicationService.class);
    private final TransactionRepository transactions;
    private final DocumentRepository documents;
    private final EmailLogRepository emails;
    private final UserRepository users;
    private final DocumentAccessService access;
    private final LocalDocumentStorage storage;
    private final BilingualPdfGenerator pdf;
    private final EmailGateway gateway;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String disposalPurchasingRecipient;
    private final String disposalAccountingRecipient;

    public TransactionPublicationService(TransactionRepository transactions, DocumentRepository documents,
            EmailLogRepository emails, UserRepository users, DocumentAccessService access,
            LocalDocumentStorage storage, BilingualPdfGenerator pdf, EmailGateway gateway,
            JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${itam.publication.disposal-purchasing-recipient:pur@itam.example}") String disposalPurchasingRecipient,
            @Value("${itam.publication.disposal-accounting-recipient:accounting@itam.example}") String disposalAccountingRecipient) {
        this.transactions = transactions; this.documents = documents; this.emails = emails; this.users = users;
        this.access = access; this.storage = storage; this.pdf = pdf; this.gateway = gateway; this.jdbc = jdbc; this.mapper = mapper;
        this.disposalPurchasingRecipient = disposalPurchasingRecipient;
        this.disposalAccountingRecipient = disposalAccountingRecipient;
    }

    public PublicationStatusResponse status(Long id) {
        var tx = transaction(id); access.requireRead(tx);
        var type = reportType(tx.getType());
        var latest = documents.findFirstByTransactionTransactionIdAndDocumentTypeOrderByPublicationVersionDesc(id, type).orElse(null);
        var failure = jdbc.query("SELECT error_message,created_at FROM publication_attempts WHERE transaction_id=? ORDER BY created_at DESC LIMIT 1",
                (rs, n) -> java.util.Map.entry(rs.getString(1), rs.getTimestamp(2).toInstant()), id).stream().findFirst().orElse(null);
        boolean failedAfterLatest = failure != null && (latest == null || latest.getIssuedAt() == null || failure.getValue().isAfter(latest.getIssuedAt()));
        var pdfStatus = latest == null
                ? new PublicationStatusResponse.PdfStatus(null, null, null, null, null, failedAfterLatest ? "FAILED" : "NOT_GENERATED",
                    failure == null ? null : failure.getKey())
                : new PublicationStatusResponse.PdfStatus(latest.getDocumentId(), latest.getOriginalFileName(),
                    latest.getPublicationVersion(), latest.getTemplateVersion(), latest.getIssuedAt(), failedAfterLatest ? "FAILED" : "READY",
                    failedAfterLatest ? failure.getKey() : null);
        return new PublicationStatusResponse(id, tx.getTransactionCode(), tx.getType().name(), pdfStatus,
                emails.findByTransactionTransactionIdOrderByCreatedAtDesc(id).stream().map(e ->
                    new PublicationStatusResponse.EmailAttempt(e.getEmailLogId(), e.getRecipient(), e.getEventType(),
                            e.getStatus().name(), e.getErrorMessage(), e.getSentAt(), e.getCreatedAt())).toList());
    }

    public DocumentDownload latestPdf(Long id) {
        var tx = transaction(id);
        var document = documents.findFirstByTransactionTransactionIdAndDocumentTypeOrderByPublicationVersionDesc(id, reportType(tx.getType()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "PUBLICATION_NOT_AVAILABLE", "Publication is not available"));
        access.requireDownload(document);
        return new DocumentDownload(DocumentResponse.fromEntity(document), storage.read(document));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PublicationStatusResponse regenerate(Long id) {
        var tx = transaction(id); access.requireRead(tx);
        requireCompleted(tx);
        generate(tx, null);
        return status(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PublicationStatusResponse resend(Long id) {
        var tx = transaction(id); access.requireRead(tx); requireCompleted(tx);
        var document = documents.findFirstByTransactionTransactionIdAndDocumentTypeOrderByPublicationVersionDesc(id, reportType(tx.getType()))
                .orElseGet(() -> generate(tx, null));
        sendCompletion(tx, document);
        return status(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onImportSubmitted(Long id) {
        var tx = transaction(id);
        String subject = "Phiếu nhập chờ kiểm tra / Receiving pending: " + tx.getTransactionCode();
        String body = "Phiên bản / Revision: " + tx.getSubmittedRevision() + "\nVui lòng kiểm tra trong ITAM / Please review in ITAM.";
        jdbc.queryForList("SELECT u.email FROM users u JOIN roles r USING(role_id) WHERE r.name='IT_STAFF' AND u.account_status='ACTIVE'", String.class)
                .forEach(recipient -> send(tx, recipient, "IMPORT_SUBMITTED", subject, body, null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onImportProcessed(Long id, boolean approved) {
        var tx = transaction(id);
        var recipient = importSubmitter(id, tx.getSubmittedRevision());
        if (approved) {
            var document = generate(tx, null);
            sendCompletion(tx, document, recipient);
        } else {
            send(tx, recipient, "IMPORT_REJECTED",
                    "Phiếu nhập bị từ chối / Receiving rejected: " + tx.getTransactionCode(),
                    "Lý do / Reason: " + (tx.getRejectionReason() == null ? "-" : tx.getRejectionReason()), null);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCompleted(Long id, Object workflowSnapshot) {
        var tx = transaction(id);
        var document = generate(tx, workflowSnapshot);
        sendCompletion(tx, document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDisposalPending(Long id) {
        var tx = transaction(id);
        String subject = "Phiếu thanh lý chờ duyệt / Disposal pending: " + tx.getTransactionCode();
        String body = "Vui lòng duyệt trong ITAM / Please review in ITAM.";
        jdbc.queryForList("SELECT u.email FROM users u JOIN roles r USING(role_id) WHERE r.code='ADMIN' AND u.account_status='ACTIVE'", String.class)
                .forEach(recipient -> send(tx, recipient, "DISPOSAL_SUBMITTED", subject, body, null));
    }

    public void afterCommit(Long transactionId, Runnable operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safely(transactionId, operation); }
            });
        } else safely(transactionId, operation);
    }

    private void safely(Long transactionId, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException ex) {
            log.error("Post-commit PDF/email publication failed", ex);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            try {
                jdbc.update("INSERT INTO publication_attempts(transaction_id,kind,status,error_message) VALUES (?,'PDF','FAILED',?)",
                        transactionId, message.substring(0, Math.min(message.length(), 4000)));
            } catch (RuntimeException loggingFailure) {
                log.warn("Unable to persist the publication failure for transaction {}", transactionId, loggingFailure);
            }
        }
    }

    private DocumentEntity generate(TransactionEntity tx, Object suppliedSnapshot) {
        DocumentType type = reportType(tx.getType());
        JsonNode snapshot = publicationSnapshot(tx, suppliedSnapshot);
        int version = Math.toIntExact(documents.countByTransactionTransactionIdAndDocumentType(tx.getTransactionId(), type) + 1);
        Instant issuedAt = Instant.now();
        String actor = tx.getProcessedBy() == null ? tx.getRequester().getFullName() : tx.getProcessedBy().getFullName();
        byte[] bytes = pdf.generate(tx.getTransactionCode(), tx.getType().name(), issuedAt, actor, snapshot);
        String fileName = tx.getTransactionCode() + "-v" + version + ".pdf";
        var stored = storage.storeGeneratedPdf(tx.getType(), fileName, bytes);
        var entity = new DocumentEntity();
        entity.setTransaction(tx); entity.setDocumentType(type); entity.setOriginalFileName(fileName);
        entity.setStoredFileName(stored.storedFileName()); entity.setStoragePath(stored.storagePath());
        entity.setMimeType(stored.mimeType()); entity.setFileSize(stored.fileSize()); entity.setChecksum(stored.checksum());
        entity.setUploadedBy(currentUserOr(tx.getProcessedBy() == null ? tx.getRequester() : tx.getProcessedBy()));
        entity.setLocked(true); entity.setPublicationVersion(version); entity.setTemplateVersion(BilingualPdfGenerator.TEMPLATE_VERSION);
        entity.setIssuedAt(issuedAt);
        return documents.saveAndFlush(entity);
    }

    private JsonNode publicationSnapshot(TransactionEntity tx, Object supplied) {
        var existing = jdbc.queryForList("SELECT snapshot::text FROM transaction_publication_snapshots WHERE transaction_id=?", String.class, tx.getTransactionId());
        if (!existing.isEmpty()) return parse(existing.getFirst());
        JsonNode value;
        if (supplied != null) value = mapper.valueToTree(supplied);
        else if (tx.getType() == TransactionType.HANDOVER) {
            // The business transaction commits this snapshot before PDF/email publication starts.
            String json = jdbc.queryForList("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?",
                    String.class, tx.getTransactionId()).stream().findFirst()
                    .orElseThrow(() -> new AppException(HttpStatus.CONFLICT, "PUBLICATION_SNAPSHOT_MISSING", "Publication snapshot is missing"));
            value = parse(json);
        }
        else if (tx.getType() == TransactionType.IMPORT) {
            String json = jdbc.queryForObject("SELECT snapshot::text FROM transaction_revisions WHERE transaction_id=? AND revision=?", String.class,
                    tx.getTransactionId(), tx.getSubmittedRevision());
            value = parse(json);
        } else throw new AppException(HttpStatus.CONFLICT, "PUBLICATION_SNAPSHOT_MISSING", "Publication snapshot is missing");
        jdbc.update("INSERT INTO transaction_publication_snapshots(transaction_id,snapshot) VALUES (?,CAST(? AS jsonb))",
                tx.getTransactionId(), value.toString());
        return value;
    }

    private void sendCompletion(TransactionEntity tx, DocumentEntity document) {
        String recipient = switch (tx.getType()) {
            case IMPORT -> importSubmitter(tx.getTransactionId(), tx.getSubmittedRevision());
            case HANDOVER -> publicationSnapshot(tx, null).path("recipientEmail").asText(null);
            case RECOVERY -> jdbc.queryForObject("SELECT u.email FROM transaction_recovery_details d JOIN users u ON u.user_id=d.returner_user_id WHERE d.transaction_id=?", String.class, tx.getTransactionId());
            case DISPOSAL -> disposalPurchasingRecipient;
            default -> throw unsupported();
        };
        sendCompletion(tx, document, recipient);
        if (tx.getType() == TransactionType.DISPOSAL && !disposalAccountingRecipient.equalsIgnoreCase(recipient)) {
            sendCompletion(tx, document, disposalAccountingRecipient);
        }
    }

    private void sendCompletion(TransactionEntity tx, DocumentEntity document, String recipient) {
        String event = tx.getType().name() + "_COMPLETED";
        String subject = "Hoàn tất phiếu / Transaction completed: " + tx.getTransactionCode();
        String body = "Biên bản song ngữ đã sẵn sàng / The bilingual report is ready.\nTệp / File: " + document.getOriginalFileName()
                + "\nĐường dẫn / Link: /api/v1/documents/" + document.getDocumentId() + "/download";
        send(tx, recipient, event, subject, body, document);
    }

    private void send(TransactionEntity tx, String recipient, String event, String subject, String body, DocumentEntity document) {
        var entry = new EmailLogEntity(); entry.setTransaction(tx); entry.setRecipient(recipient == null ? "" : recipient);
        entry.setEventType(event); entry.setSubject(subject); entry.setContent(body); entry.setDocument(document); entry.setStatus(EmailStatus.PENDING);
        emails.saveAndFlush(entry);
        try {
            gateway.send(recipient, subject, body); entry.setStatus(EmailStatus.SENT); entry.setSentAt(Instant.now());
        } catch (RuntimeException ex) {
            entry.setStatus(EmailStatus.FAILED); entry.setErrorMessage(ex.getMessage());
        }
        emails.save(entry);
    }

    private String importSubmitter(Long id, int revision) {
        return jdbc.queryForObject("SELECT u.email FROM transaction_revisions r JOIN users u ON u.user_id=r.submitted_by WHERE r.transaction_id=? AND r.revision=?",
                String.class, id, revision);
    }

    private UserEntity currentUserOr(UserEntity fallback) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? fallback : users.findByEmail(auth.getName()).orElse(fallback);
    }

    private TransactionEntity transaction(Long id) {
        return transactions.findById(id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found"));
    }
    private void requireCompleted(TransactionEntity tx) {
        if (tx.getStatus() != TransactionStatus.COMPLETED) throw unsupported();
    }
    private DocumentType reportType(TransactionType type) {
        return switch (type) {
            case IMPORT -> DocumentType.IMPORT_RECEIPT;
            case HANDOVER -> DocumentType.HANDOVER_REPORT;
            case RECOVERY -> DocumentType.RECOVERY_REPORT;
            case DISPOSAL -> DocumentType.DISPOSAL_REPORT;
            default -> throw unsupported();
        };
    }
    private AppException unsupported() { return new AppException(HttpStatus.CONFLICT, "PUBLICATION_NOT_AVAILABLE", "Publication is not available"); }
    private JsonNode parse(String json) {
        try { return mapper.readTree(json); } catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid publication snapshot", ex); }
    }
}
