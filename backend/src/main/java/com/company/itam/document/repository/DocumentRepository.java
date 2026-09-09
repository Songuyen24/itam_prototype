package com.company.itam.document.repository;

import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.common.enums.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByTransactionTransactionId(Long transactionId);
    @EntityGraph(attributePaths = {"transaction", "asset", "uploadedBy"})
    Page<DocumentEntity> findByTransactionTransactionId(Long transactionId, Pageable pageable);
    List<DocumentEntity> findByAssetAssetId(Long assetId);
    Page<DocumentEntity> findByDocumentType(DocumentType documentType, Pageable pageable);
    Page<DocumentEntity> findByUploadedByUserId(Long userId, Pageable pageable);
    @org.springframework.data.jpa.repository.Query(value = "SELECT d.* FROM documents d JOIN transaction_document_links l ON l.document_id=d.document_id WHERE l.transaction_id=:id ORDER BY d.created_at DESC, d.document_id DESC",
        countQuery = "SELECT count(*) FROM transaction_document_links WHERE transaction_id=:id", nativeQuery = true)
    Page<DocumentEntity> findLinked(@org.springframework.data.repository.query.Param("id") Long id, Pageable pageable);
}
