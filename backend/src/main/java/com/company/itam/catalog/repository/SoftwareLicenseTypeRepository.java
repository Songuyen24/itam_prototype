package com.company.itam.catalog.repository;

import com.company.itam.catalog.entity.SoftwareLicenseTypeEntity;
import com.company.itam.common.enums.LicenseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SoftwareLicenseTypeRepository extends JpaRepository<SoftwareLicenseTypeEntity, Long> {
    Optional<SoftwareLicenseTypeEntity> findByCode(LicenseType code);
    boolean existsByCode(LicenseType code);
}
