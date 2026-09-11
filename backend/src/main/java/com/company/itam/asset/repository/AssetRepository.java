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
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"type.category","status","assignedTo","department","location","supplier","hardwareDetails.model","hardwareDetails.condition","licenseDetails.assignmentType"})
    Page<AssetEntity> findAll(org.springframework.data.jpa.domain.Specification<AssetEntity> specification, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AssetEntity a WHERE a.assetId = :id")
    Optional<AssetEntity> lockById(@Param("id") Long id);

    @Query(value = "SELECT next_asset_tag()", nativeQuery = true)
    String nextGeneratedAssetTag();

    Optional<AssetEntity> findByAssetTag(String assetTag);
    @Query(value="SELECT EXISTS(SELECT 1 FROM transaction_assets a JOIN transactions t USING(transaction_id) WHERE a.asset_id=:id AND t.type='IMPORT')",nativeQuery=true)
    boolean hasReceivingHistory(@Param("id") Long id);
    boolean existsByAssetTag(String assetTag);

    Page<AssetEntity> findByStatusCode(AssetStatus status, Pageable pageable);
    Page<AssetEntity> findByTypeCategoryCode(AssetCategory category, Pageable pageable);
    Page<AssetEntity> findByAssignedToUserId(Long userId, Pageable pageable);
    Page<AssetEntity> findByAssignedToUserIdAndStatusCode(Long userId, AssetStatus status, Pageable pageable);

    /**
     * Recovery candidates owned by / attached to a user (DEVICE assigned, COMPONENT linked to a
     * device assigned to the user, LICENSE PER_USER with an allocation on one of those devices).
     * OEM licenses and assets already in retired/imported states are excluded.
     * Sorted by category code so DEVICE / COMPONENT / LICENSE appear in a stable order.
     */
    @Query(value = """
            SELECT DISTINCT a.* FROM assets a
            LEFT JOIN asset_types t ON a.type_id = t.type_id
            LEFT JOIN asset_categories c ON t.category_id = c.category_id
            WHERE a.deleted_at IS NULL
              AND (
                -- DEVICE assigned to the user, status IN_USE
                (c.code = 'DEVICE' AND a.assigned_to = :userId AND a.status_id IN (
                    SELECT status_id FROM asset_statuses WHERE code = 'IN_USE'
                ))
                OR
                -- COMPONENT linked to a DEVICE of the user (status not RETIRED)
                (c.code = 'COMPONENT' AND EXISTS (
                    SELECT 1 FROM asset_relationships r
                    JOIN assets d ON d.asset_id = r.parent_asset_id
                    WHERE r.child_asset_id = a.asset_id
                      AND r.relationship_type = 'COMPONENT_OF'
                      AND d.assigned_to = :userId
                      AND d.deleted_at IS NULL
                ) AND a.status_id NOT IN (
                    SELECT status_id FROM asset_statuses WHERE code = 'RETIRED'
                ))
                OR
                -- LICENSE PER_USER with allocation on a DEVICE of the user (allocation status not RELEASED)
                (c.code = 'LICENSE' AND EXISTS (
                    SELECT 1 FROM license_allocations la
                    JOIN assets d ON d.asset_id = la.device_asset_id
                    WHERE la.license_asset_id = a.asset_id
                      AND d.assigned_to = :userId
                      AND d.deleted_at IS NULL
                      AND la.status <> 'RELEASED'
                ) AND a.id NOT IN (
                    -- exclude OEM licenses
                    SELECT ld.asset_id FROM license_details ld
                    JOIN license_assignment_types at ON at.assignment_type_id = ld.assignment_type_id
                    WHERE at.code = 'OEM'
                ))
              )
              AND (
                :keyword IS NULL OR :keyword = '' OR
                LOWER(a.asset_tag) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY CASE c.code WHEN 'DEVICE' THEN 1 WHEN 'COMPONENT' THEN 2 WHEN 'LICENSE' THEN 3 ELSE 4 END, a.asset_tag
            """,
            countQuery = """
            SELECT COUNT(DISTINCT a.asset_id) FROM assets a
            LEFT JOIN asset_types t ON a.type_id = t.type_id
            LEFT JOIN asset_categories c ON t.category_id = c.category_id
            WHERE a.deleted_at IS NULL
              AND (
                (c.code = 'DEVICE' AND a.assigned_to = :userId AND a.status_id IN (
                    SELECT status_id FROM asset_statuses WHERE code = 'IN_USE'
                ))
                OR
                (c.code = 'COMPONENT' AND EXISTS (
                    SELECT 1 FROM asset_relationships r
                    JOIN assets d ON d.asset_id = r.parent_asset_id
                    WHERE r.child_asset_id = a.asset_id
                      AND r.relationship_type = 'COMPONENT_OF'
                      AND d.assigned_to = :userId
                      AND d.deleted_at IS NULL
                ) AND a.status_id NOT IN (
                    SELECT status_id FROM asset_statuses WHERE code = 'RETIRED'
                ))
                OR
                (c.code = 'LICENSE' AND EXISTS (
                    SELECT 1 FROM license_allocations la
                    JOIN assets d ON d.asset_id = la.device_asset_id
                    WHERE la.license_asset_id = a.asset_id
                      AND d.assigned_to = :userId
                      AND d.deleted_at IS NULL
                      AND la.status <> 'RELEASED'
                ) AND a.id NOT IN (
                    SELECT ld.asset_id FROM license_details ld
                    JOIN license_assignment_types at ON at.assignment_type_id = ld.assignment_type_id
                    WHERE at.code = 'OEM'
                ))
              )
              AND (
                :keyword IS NULL OR :keyword = '' OR
                LOWER(a.asset_tag) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """,
            nativeQuery = true)
    Page<AssetEntity> findRecoveryCandidates(@Param("userId") Long userId,
                                             @Param("keyword") String keyword,
                                             Pageable pageable);
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
