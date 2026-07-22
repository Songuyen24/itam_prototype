package com.company.itam.importbatch.repository;

import com.company.itam.importbatch.entity.ImportRowEntity;
import com.company.itam.common.enums.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportRowRepository extends JpaRepository<ImportRowEntity, Long> {
    List<ImportRowEntity> findByImportBatchImportBatchId(Long importBatchId);
    List<ImportRowEntity> findByImportBatchImportBatchIdAndValidationStatus(Long importBatchId, ValidationStatus validationStatus);
}
