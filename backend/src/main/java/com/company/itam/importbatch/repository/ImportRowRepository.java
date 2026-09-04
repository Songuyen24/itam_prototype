package com.company.itam.importbatch.repository;

import com.company.itam.importbatch.entity.ImportRowEntity;
import com.company.itam.common.enums.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportRowRepository extends JpaRepository<ImportRowEntity, Long> {

    /**
     * List rows for a batch with asset eagerly loaded (only assetId needed, but
     * Hibernate can avoid N+1 by fetching the proxy upfront).
     */
    @Query("SELECT r FROM ImportRowEntity r LEFT JOIN FETCH r.asset WHERE r.importBatch.importBatchId = :batchId")
    List<ImportRowEntity> findByImportBatchImportBatchIdWithAsset(@Param("batchId") Long batchId);

    /**
     * List rows filtered by status with asset eagerly loaded.
     */
    @Query("SELECT r FROM ImportRowEntity r LEFT JOIN FETCH r.asset " +
           "WHERE r.importBatch.importBatchId = :batchId AND r.validationStatus = :status")
    List<ImportRowEntity> findByImportBatchImportBatchIdAndValidationStatusWithAsset(
            @Param("batchId") Long batchId,
            @Param("status") ValidationStatus status);

    List<ImportRowEntity> findByImportBatchImportBatchId(Long importBatchId);
    List<ImportRowEntity> findByImportBatchImportBatchIdAndValidationStatus(Long importBatchId, ValidationStatus validationStatus);
}
