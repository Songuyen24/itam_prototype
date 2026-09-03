package com.company.itam.catalog.repository;

import com.company.itam.catalog.entity.LicenseTermTypeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenseTermTypeRepository extends JpaRepository<LicenseTermTypeEntity, Long> {
    Optional<LicenseTermTypeEntity> findByCode(String code);
    boolean existsByCode(String code);
}
