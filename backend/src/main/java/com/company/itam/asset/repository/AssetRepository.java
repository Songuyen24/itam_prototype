package com.company.itam.asset.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.common.enums.AssetCategory;
import com.company.itam.common.enums.AssetStatus;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, Long>, JpaSpecificationExecutor<AssetEntity> {

    @Override
    @EntityGraph(
            attributePaths = {
                "type.category",
                "status",
                "assignedTo",
                "department",
                "location",
                "supplier",
                "hardwareDetails.model",
                "hardwareDetails.condition",
                "licenseDetails.assignmentType"
            })
    Page<AssetEntity> findAll(Specification<AssetEntity> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AssetEntity a WHERE a.assetId = :id")
    Optional<AssetEntity> lockById(@Param("id") Long id);

    @Query(value = "SELECT next_asset_tag()", nativeQuery = true)
    String nextGeneratedAssetTag();

    Optional<AssetEntity> findByAssetTag(String assetTag);

    @Query(
            value =
                    "SELECT EXISTS(SELECT 1 FROM transaction_assets a JOIN transactions t USING(transaction_id) WHERE a.asset_id=:id AND t.type='IMPORT' AND t.status IN ('DRAFT','PENDING'))",
            nativeQuery = true)
    boolean hasActiveReceiving(@Param("id") Long id);

    @Query(
            value =
                    "SELECT EXISTS(SELECT 1 FROM handover_asset_reservations WHERE asset_id=:id) OR EXISTS(SELECT 1 FROM transaction_assets ta JOIN transactions t USING(transaction_id) WHERE ta.asset_id=:id AND t.type='HANDOVER' AND t.status='PENDING' AND NOT EXISTS(SELECT 1 FROM workflow_progress wp WHERE wp.transaction_id=t.transaction_id))",
            nativeQuery = true)
    boolean hasPendingHandover(@Param("id") Long id);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM recovery_asset_reservations WHERE asset_id=:id)", nativeQuery = true)
    boolean hasPendingRecovery(@Param("id") Long id);

    @Query(value = "SELECT asset_id FROM recovery_asset_reservations WHERE asset_id IN (:ids)", nativeQuery = true)
    List<Long> pendingRecoveryAssetIds(@Param("ids") Collection<Long> ids);

    @Query(
            value =
                    "SELECT asset_id FROM handover_asset_reservations WHERE asset_id IN (:ids) UNION SELECT DISTINCT ta.asset_id FROM transaction_assets ta JOIN transactions t USING(transaction_id) WHERE ta.asset_id IN (:ids) AND t.type='HANDOVER' AND t.status='PENDING' AND NOT EXISTS(SELECT 1 FROM workflow_progress wp WHERE wp.transaction_id=t.transaction_id)",
            nativeQuery = true)
    List<Long> pendingHandoverAssetIds(@Param("ids") Collection<Long> ids);

    @Query(
            value =
                    "SELECT EXISTS(SELECT 1 FROM transaction_assets ta JOIN transactions t USING(transaction_id) WHERE ta.asset_id=:id AND t.type='DISPOSAL' AND t.status IN ('PENDING','APPROVED','PARTIAL') AND (NOT EXISTS(SELECT 1 FROM workflow_progress wp WHERE wp.transaction_id=t.transaction_id) OR EXISTS(SELECT 1 FROM workflow_progress wp CROSS JOIN LATERAL jsonb_array_elements(wp.groups) g WHERE wp.transaction_id=t.transaction_id AND g->>'status' IN ('PENDING','APPROVED') AND g->'assetIds' @> to_jsonb(ARRAY[ta.asset_id]))))",
            nativeQuery = true)
    boolean hasPendingDisposal(@Param("id") Long id);

    @Query(
            value =
                    "SELECT DISTINCT ta.asset_id FROM transaction_assets ta JOIN transactions t USING(transaction_id) WHERE ta.asset_id IN (:ids) AND t.type='DISPOSAL' AND t.status IN ('PENDING','APPROVED','PARTIAL') AND (NOT EXISTS(SELECT 1 FROM workflow_progress wp WHERE wp.transaction_id=t.transaction_id) OR EXISTS(SELECT 1 FROM workflow_progress wp CROSS JOIN LATERAL jsonb_array_elements(wp.groups) g WHERE wp.transaction_id=t.transaction_id AND g->>'status' IN ('PENDING','APPROVED') AND g->'assetIds' @> to_jsonb(ARRAY[ta.asset_id])))",
            nativeQuery = true)
    List<Long> pendingDisposalAssetIds(@Param("ids") Collection<Long> ids);

    boolean existsByAssetTag(String assetTag);

    boolean existsByAssetTagIgnoreCase(String assetTag);

    Page<AssetEntity> findByStatusCode(AssetStatus status, Pageable pageable);

    Page<AssetEntity> findByTypeCategoryCode(AssetCategory category, Pageable pageable);

    Page<AssetEntity> findByAssignedToUserId(Long userId, Pageable pageable);

    Page<AssetEntity> findByAssignedToUserIdAndStatusCode(Long userId, AssetStatus status, Pageable pageable);

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
