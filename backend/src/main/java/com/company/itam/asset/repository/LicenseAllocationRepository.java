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
    @EntityGraph(attributePaths={"device","user"})
    Page<LicenseAllocationEntity> findByLicenseAssetId(Long id, Pageable pageable);
    boolean existsByLicenseAssetId(Long id);
}
