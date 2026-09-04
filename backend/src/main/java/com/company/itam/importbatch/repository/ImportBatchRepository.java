package com.company.itam.importbatch.repository;

import com.company.itam.importbatch.entity.ImportBatchEntity;
import com.company.itam.common.enums.ImportBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, Long> {

    /**
     * List all batches with uploadedBy eagerly loaded.
     * Prevents LazyInitializationException + N+1 in AssetImportService.fillBatchResponse().
     */
    @Query(value = "SELECT b FROM ImportBatchEntity b LEFT JOIN FETCH b.uploadedBy",
           countQuery = "SELECT COUNT(b) FROM ImportBatchEntity b")
    Page<ImportBatchEntity> findAllWithUploadedBy(Pageable pageable);

    /**
     * List batches by status with uploadedBy eagerly loaded.
     */
    @Query("SELECT b FROM ImportBatchEntity b LEFT JOIN FETCH b.uploadedBy WHERE b.status = :status")
    Page<ImportBatchEntity> findByStatusWithUploadedBy(@Param("status") ImportBatchStatus status, Pageable pageable);

    /**
     * List batches by user with uploadedBy eagerly loaded.
     */
    @Query("SELECT b FROM ImportBatchEntity b LEFT JOIN FETCH b.uploadedBy WHERE b.uploadedBy.userId = :userId")
    Page<ImportBatchEntity> findByUploadedByUserIdWithUploadedBy(@Param("userId") Long userId, Pageable pageable);

    /**
     * Get single batch by ID with uploadedBy eagerly loaded.
     */
    @Query("SELECT b FROM ImportBatchEntity b LEFT JOIN FETCH b.uploadedBy WHERE b.importBatchId = :id")
    Optional<ImportBatchEntity> findByIdWithUploadedBy(@Param("id") Long id);

    Page<ImportBatchEntity> findByStatus(ImportBatchStatus status, Pageable pageable);
    Page<ImportBatchEntity> findByUploadedByUserId(Long userId, Pageable pageable);
}
