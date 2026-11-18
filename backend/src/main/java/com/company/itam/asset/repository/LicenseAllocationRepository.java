package com.company.itam.asset.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.itam.asset.entity.LicenseAllocationEntity;
import com.company.itam.asset.entity.LicenseAllocationStatus;

public interface LicenseAllocationRepository extends JpaRepository<LicenseAllocationEntity, Long> {

    @Query("SELECT a.license.assetId FROM LicenseAllocationEntity a WHERE a.allocationId = :id")
    Optional<Long> licenseId(@Param("id") Long id);

    boolean existsByLicenseAssetIdAndUserUserIdAndStatus(Long licenseId, Long userId, LicenseAllocationStatus status);

    @Query(
            "SELECT COALESCE(SUM(a.seatCount), 0) FROM LicenseAllocationEntity a WHERE a.license.assetId = :id AND a.status <> 'RELEASED'")
    long usedSeats(@Param("id") Long id);

    @Query(
            "SELECT a.license.assetId, SUM(a.seatCount) FROM LicenseAllocationEntity a WHERE a.license.assetId IN :ids AND a.status <> 'RELEASED' GROUP BY a.license.assetId")
    List<Object[]> usedSeatsByLicenseIds(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = {"license", "device", "user"})
    Page<LicenseAllocationEntity> findByLicenseAssetId(Long id, Pageable pageable);

    @EntityGraph(attributePaths = {"license", "device", "user"})
    List<LicenseAllocationEntity> findAllByLicenseAssetId(Long licenseAssetId);

    boolean existsByLicenseAssetId(Long id);

    @EntityGraph(attributePaths = {"license", "device", "user"})
    List<LicenseAllocationEntity> findByDeviceAssetId(Long deviceAssetId);

    @EntityGraph(attributePaths = {"license", "device", "user"})
    Optional<LicenseAllocationEntity> findByLicenseAssetIdAndDeviceAssetId(Long licenseAssetId, Long deviceAssetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"license", "device", "user"})
    @Query("SELECT a FROM LicenseAllocationEntity a WHERE a.allocationId IN :ids ORDER BY a.allocationId")
    List<LicenseAllocationEntity> lockAllById(@Param("ids") Collection<Long> ids);
}
