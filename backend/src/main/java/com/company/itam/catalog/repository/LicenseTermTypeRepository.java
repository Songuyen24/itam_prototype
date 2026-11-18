package com.company.itam.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.itam.catalog.entity.LicenseTermTypeEntity;

public interface LicenseTermTypeRepository extends JpaRepository<LicenseTermTypeEntity, Long> {
    Optional<LicenseTermTypeEntity> findByCode(String code);

    boolean existsByCode(String code);
}
