package com.company.itam.asset.relationship.entity;

import com.company.itam.asset.entity.AssetEntity;
import com.company.itam.asset.relationship.enums.RelationshipType;
import com.company.itam.user.entity.UserEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "asset_relationships")
public class AssetRelationshipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relationship_id")
    private Long relationshipId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id")
    private com.company.itam.asset.entity.LicenseAllocationEntity allocation;
    public com.company.itam.asset.entity.LicenseAllocationEntity getAllocation() { return allocation; }
    public void setAllocation(com.company.itam.asset.entity.LicenseAllocationEntity value) { allocation = value; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_asset_id", nullable = false)
    private AssetEntity parentAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_asset_id", nullable = false)
    private AssetEntity childAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 30)
    private RelationshipType relationshipType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public AssetRelationshipEntity() {}

    public Long getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(Long relationshipId) {
        this.relationshipId = relationshipId;
    }

    public AssetEntity getParentAsset() {
        return parentAsset;
    }

    public void setParentAsset(AssetEntity parentAsset) {
        this.parentAsset = parentAsset;
    }

    public AssetEntity getChildAsset() {
        return childAsset;
    }

    public void setChildAsset(AssetEntity childAsset) {
        this.childAsset = childAsset;
    }

    public RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(RelationshipType relationshipType) {
        this.relationshipType = relationshipType;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserEntity createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
