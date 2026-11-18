package com.company.itam.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.itam.catalog.entity.LicenseAssignmentTypeEntity;

public interface LicenseAssignmentTypeRepository extends JpaRepository<LicenseAssignmentTypeEntity, Long> {
    Optional<LicenseAssignmentTypeEntity> findByCode(String code);

    boolean existsByCode(String code);
}
