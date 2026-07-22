package com.company.itam.asset.repository;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.enums.AssetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, Long>, JpaSpecificationExecutor<AssetEntity> {
    Optional<AssetEntity> findByAssetTag(String assetTag);
    boolean existsByAssetTag(String assetTag);

    Page<AssetEntity> findByStatusId(AssetStatus status, Pageable pageable);
    Page<AssetEntity> findByCategoryId(AssetCategory category, Pageable pageable);
    Page<AssetEntity> findByAssignedToUserId(Long userId, Pageable pageable);
    Page<AssetEntity> findByDepartmentDepartmentId(Long departmentId, Pageable pageable);
    Page<AssetEntity> findByLocationLocationId(Long locationId, Pageable pageable);

    @Query("SELECT a FROM AssetEntity a LEFT JOIN FETCH a.hardwareDetails WHERE a.assetId = :id")
    Optional<AssetEntity> findByIdWithHardwareDetails(@Param("id") Long id);

    @Query("SELECT a FROM AssetEntity a LEFT JOIN FETCH a.softwareDetails WHERE a.assetId = :id")
    Optional<AssetEntity> findByIdWithSoftwareDetails(@Param("id") Long id);
}
