package com.company.itam.relationship.repository;

import com.company.itam.relationship.entity.AssetRelationshipEntity;
import com.company.itam.common.enums.RelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRelationshipRepository extends JpaRepository<AssetRelationshipEntity, Long> {
    List<AssetRelationshipEntity> findByParentAssetAssetId(Long parentAssetId);
    List<AssetRelationshipEntity> findByChildAssetAssetId(Long childAssetId);

    @Query("SELECT r FROM AssetRelationshipEntity r WHERE r.parentAsset.assetId = :parentId AND r.relationshipType = :type")
    List<AssetRelationshipEntity> findByParentAndType(@Param("parentId") Long parentId, @Param("type") RelationshipType type);

    @Query("SELECT r FROM AssetRelationshipEntity r WHERE r.childAsset.assetId = :childId AND r.relationshipType = :type")
    List<AssetRelationshipEntity> findByChildAndType(@Param("childId") Long childId, @Param("type") RelationshipType type);

    boolean existsByParentAssetAssetIdAndChildAssetAssetIdAndRelationshipType(
            Long parentAssetId, Long childAssetId, RelationshipType relationshipType);
}
