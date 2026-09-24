package com.company.itam.catalog.repository;

import com.company.itam.catalog.entity.LicenseAssignmentTypeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenseAssignmentTypeRepository extends JpaRepository<LicenseAssignmentTypeEntity, Long> {
    Optional<LicenseAssignmentTypeEntity> findByCode(String code);
    boolean existsByCode(String code);
}
