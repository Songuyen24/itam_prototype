package com.company.itam.asset.relationship.repository;

import com.company.itam.asset.relationship.entity.AssetRelationshipEntity;
import com.company.itam.asset.relationship.enums.RelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRelationshipRepository extends JpaRepository<AssetRelationshipEntity, Long> {
    @Query("SELECT r.parentAsset.assetId, r.childAsset.assetId FROM AssetRelationshipEntity r WHERE r.relationshipId=:id")
    List<Object[]> endpointIds(@Param("id") Long id);
    java.util.Optional<AssetRelationshipEntity> findByAllocationAllocationId(Long id);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"parentAsset.type.category","parentAsset.status","parentAsset.assignedTo","childAsset.type.category","childAsset.status","childAsset.assignedTo","childAsset.licenseDetails.assignmentType","allocation"})
    @Query("SELECT r FROM AssetRelationshipEntity r WHERE r.parentAsset.assetId=:id OR r.childAsset.assetId=:id")
    org.springframework.data.domain.Page<AssetRelationshipEntity> findAllForAsset(@Param("id") Long id, org.springframework.data.domain.Pageable pageable);
    List<AssetRelationshipEntity> findByParentAssetAssetId(Long parentAssetId);
    List<AssetRelationshipEntity> findByChildAssetAssetId(Long childAssetId);

    @Query("SELECT r FROM AssetRelationshipEntity r WHERE r.parentAsset.assetId IN :ids")
    List<AssetRelationshipEntity> findByParentAssetAssetIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT r FROM AssetRelationshipEntity r WHERE r.childAsset.assetId IN :ids")
    List<AssetRelationshipEntity> findByChildAssetAssetIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT r FROM AssetRelationshipEntity r WHERE r.parentAsset.assetId = :parentId AND r.relationshipType = :type")
    List<AssetRelationshipEntity> findByParentAndType(@Param("parentId") Long parentId, @Param("type") RelationshipType type);

    @Query("SELECT r FROM AssetRelationshipEntity r WHERE r.childAsset.assetId = :childId AND r.relationshipType = :type")
    List<AssetRelationshipEntity> findByChildAndType(@Param("childId") Long childId, @Param("type") RelationshipType type);

    boolean existsByParentAssetAssetIdAndChildAssetAssetIdAndRelationshipType(
            Long parentAssetId, Long childAssetId, RelationshipType relationshipType);
}
