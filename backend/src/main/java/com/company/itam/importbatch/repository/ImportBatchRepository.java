package com.company.itam.importbatch.repository;

import com.company.itam.importbatch.entity.ImportBatchEntity;
import com.company.itam.common.enums.ImportBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, Long> {
    Page<ImportBatchEntity> findByStatus(ImportBatchStatus status, Pageable pageable);
    Page<ImportBatchEntity> findByUploadedByUserId(Long userId, Pageable pageable);
}
