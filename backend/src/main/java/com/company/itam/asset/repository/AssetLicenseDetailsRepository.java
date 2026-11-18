package com.company.itam.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.itam.asset.entity.AssetLicenseDetailsEntity;

@Repository
public interface AssetLicenseDetailsRepository extends JpaRepository<AssetLicenseDetailsEntity, Long> {
    boolean existsBySoftwareCatalogSoftwareCatalogId(Long softwareCatalogId);

    boolean existsByAssignmentTypeId(Long assignmentTypeId);

    boolean existsByTermTypeId(Long termTypeId);
}
