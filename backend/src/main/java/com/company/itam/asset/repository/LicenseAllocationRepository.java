package com.company.itam.asset.repository;

import com.company.itam.asset.entity.LicenseAllocationEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;

public interface LicenseAllocationRepository extends JpaRepository<LicenseAllocationEntity,Long> {
    @Query("SELECT a.license.assetId FROM LicenseAllocationEntity a WHERE a.allocationId=:id")
    java.util.Optional<Long> licenseId(@Param("id") Long id);
    boolean existsByLicenseAssetIdAndUserUserIdAndStatus(Long licenseId,Long userId,com.company.itam.asset.entity.LicenseAllocationStatus status);
    @Query("SELECT COALESCE(SUM(a.seatCount),0) FROM LicenseAllocationEntity a WHERE a.license.assetId=:id AND a.status <> 'RELEASED'")
    long usedSeats(@Param("id") Long id);
    @Query("SELECT a.license.assetId, SUM(a.seatCount) FROM LicenseAllocationEntity a WHERE a.license.assetId IN :ids AND a.status <> 'RELEASED' GROUP BY a.license.assetId")
    java.util.List<Object[]> usedSeatsByLicenseIds(@Param("ids") java.util.Collection<Long> ids);
    @EntityGraph(attributePaths={"license","device","user"})
    Page<LicenseAllocationEntity> findByLicenseAssetId(Long id, Pageable pageable);
    @EntityGraph(attributePaths={"license","device","user"})
    java.util.List<LicenseAllocationEntity> findAllByLicenseAssetId(Long licenseAssetId);
    boolean existsByLicenseAssetId(Long id);

    @EntityGraph(attributePaths={"license","device","user"})
    java.util.List<LicenseAllocationEntity> findByDeviceAssetId(Long deviceAssetId);

    @EntityGraph(attributePaths={"license","device","user"})
    java.util.Optional<LicenseAllocationEntity> findByLicenseAssetIdAndDeviceAssetId(Long licenseAssetId, Long deviceAssetId);
}
