package com.company.itam.asset.repository;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.enums.AssetCondition;
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
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"type.category","status","assignedTo","department","location","supplier","hardwareDetails.model","hardwareDetails.condition"})
    Page<AssetEntity> findAll(org.springframework.data.jpa.domain.Specification<AssetEntity> specification, Pageable pageable);

    @Query(value = "SELECT next_asset_tag()", nativeQuery = true)
    String nextGeneratedAssetTag();

    Optional<AssetEntity> findByAssetTag(String assetTag);
    @Query(value="SELECT EXISTS(SELECT 1 FROM transaction_assets a JOIN transactions t USING(transaction_id) WHERE a.asset_id=:id AND t.type='IMPORT')",nativeQuery=true)
    boolean hasReceivingHistory(@Param("id") Long id);
    boolean existsByAssetTag(String assetTag);

    Page<AssetEntity> findByStatusCode(AssetStatus status, Pageable pageable);
    Page<AssetEntity> findByTypeCategoryCode(AssetCategory category, Pageable pageable);
    Page<AssetEntity> findByAssignedToUserId(Long userId, Pageable pageable);
    Page<AssetEntity> findByDepartmentDepartmentId(Long departmentId, Pageable pageable);
    Page<AssetEntity> findByLocationLocationId(Long locationId, Pageable pageable);

    boolean existsByDepartmentDepartmentId(Long departmentId);
    boolean existsByLocationLocationId(Long locationId);
    boolean existsBySupplierSupplierId(Long supplierId);
    boolean existsByTypeTypeId(Long typeId);
    boolean existsByStatusCode(AssetStatus status);

    @Query("SELECT a FROM AssetEntity a LEFT JOIN FETCH a.hardwareDetails WHERE a.assetId = :id")
    Optional<AssetEntity> findByIdWithHardwareDetails(@Param("id") Long id);

    @Query("SELECT a FROM AssetEntity a LEFT JOIN FETCH a.licenseDetails WHERE a.assetId = :id")
    Optional<AssetEntity> findByIdWithLicenseDetails(@Param("id") Long id);
}
