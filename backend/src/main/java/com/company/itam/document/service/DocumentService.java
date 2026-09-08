package com.company.itam.document.service;

import com.company.itam.common.enums.DocumentType;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.document.dto.DocumentDownload;
import com.company.itam.document.dto.DocumentResponse;
import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.document.repository.DocumentRepository;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final TransactionRepository transactionRepository;
    private final DocumentAccessService accessService;
    private final LocalDocumentStorage storage;

    public DocumentService(DocumentRepository documentRepository,
                           TransactionRepository transactionRepository,
                           DocumentAccessService accessService,
                           LocalDocumentStorage storage) {
        this.documentRepository = documentRepository;
        this.transactionRepository = transactionRepository;
        this.accessService = accessService;
        this.storage = storage;
    }

    public PageResponse<DocumentResponse> getDocuments(Long transactionId, int page, int size) {
        accessService.requireTransactionReader();
        TransactionEntity transaction = findTransaction(transactionId);
        accessService.requireRead(transaction);
        validatePage(page, size);
        return PageResponse.of(documentRepository.findByTransactionTransactionId(transactionId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "documentId")))
                .map(DocumentResponse::fromEntity));
    }

    public DocumentResponse getDocument(Long documentId) {
        accessService.requireTransactionReader();
        DocumentEntity document = findDocument(documentId);
        accessService.requireRead(document.getTransaction());
        return DocumentResponse.fromEntity(document);
    }

    public DocumentDownload download(Long documentId) {
        DocumentEntity document = findDocument(documentId);
        accessService.requireDownload(document);
        return new DocumentDownload(DocumentResponse.fromEntity(document), storage.read(document));
    }

    public DocumentResponse upload(MultipartFile file, Long transactionId, DocumentType documentType,
                                   Long assetId, Long expectedVersion) {
        TransactionEntity transaction = findTransaction(transactionId);
        accessService.requireImportEditor(transaction);
        if (assetId != null) {
            validateId(assetId);
        }
        if (expectedVersion != null && expectedVersion < 0) {
            throw invalidRequest();
        }
        if (file == null || file.isEmpty() || documentType == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_INVALID", "Invalid document file");
        }
        // T14 owns DRAFT and atomic revision checks; PENDING is never an editable substitute.
        throw workflowNotReady();
    }

    public void delete(Long documentId) {
        DocumentEntity document = findDocument(documentId);
        accessService.requireImportEditor(document.getTransaction());
        if (Boolean.TRUE.equals(document.getLocked())
                || document.getTransaction().getStatus() == TransactionStatus.COMPLETED
                || document.getTransaction().getStatus() == TransactionStatus.REJECTED) {
            throw new AppException(HttpStatus.CONFLICT, "DOCUMENT_LOCKED", "Issued or historical documents cannot be changed");
        }
        throw workflowNotReady();
    }

    private DocumentEntity findDocument(Long id) {
        validateId(id);
        return documentRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found"));
    }

    private TransactionEntity findTransaction(Long id) {
        validateId(id);
        return transactionRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found"));
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw invalidRequest();
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalidRequest();
        }
    }

    private AppException invalidRequest() {
        return new AppException(HttpStatus.BAD_REQUEST, "DOCUMENT_REQUEST_INVALID", "Invalid document request");
    }

    private AppException workflowNotReady() {
        return new AppException(HttpStatus.CONFLICT, "DOCUMENT_WORKFLOW_NOT_READY",
                "Document changes require the receiving draft and revision workflow");
    }
}
