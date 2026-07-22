package com.company.itam.document.repository;

import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.common.enums.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByTransactionTransactionId(Long transactionId);
    List<DocumentEntity> findByAssetAssetId(Long assetId);
    Page<DocumentEntity> findByDocumentType(DocumentType documentType, Pageable pageable);
    Page<DocumentEntity> findByUploadedByUserId(Long userId, Pageable pageable);
}
