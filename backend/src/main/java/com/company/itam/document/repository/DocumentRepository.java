package com.company.itam.document.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.company.itam.common.enums.DocumentType;
import com.company.itam.document.entity.DocumentEntity;

@Repository
public interface DocumentRepository
        extends JpaRepository<DocumentEntity, Long>, JpaSpecificationExecutor<DocumentEntity> {
    @Query(
            value =
                    "SELECT d.* FROM documents d JOIN transaction_document_links l ON l.document_id=d.document_id WHERE l.transaction_id=:id ORDER BY d.created_at DESC, d.document_id DESC",
            countQuery = "SELECT count(*) FROM transaction_document_links WHERE transaction_id=:id",
            nativeQuery = true)
    Page<DocumentEntity> findLinked(@Param("id") Long id, Pageable pageable);

    Optional<DocumentEntity>
            findFirstByTransactionTransactionIdAndDocumentTypeAndPublicationVersionIsNotNullOrderByPublicationVersionDesc(
                    Long transactionId, DocumentType documentType);

    long countByTransactionTransactionIdAndDocumentTypeAndPublicationVersionIsNotNull(
            Long transactionId, DocumentType documentType);
}
