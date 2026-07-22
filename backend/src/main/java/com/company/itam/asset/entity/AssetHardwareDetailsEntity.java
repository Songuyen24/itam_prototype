package com.company.itam.asset.entity;

import com.company.itam.catalog.entity.AssetConditionEntity;
import com.company.itam.catalog.entity.ModelEntity;
import com.company.itam.common.enums.AssetCondition;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "asset_hardware_details")
public class AssetHardwareDetailsEntity {

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "serial_number", nullable = false, unique = true)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private ModelEntity model;

    @Column(name = "warranty_expiration")
    private LocalDate warrantyExpiration;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_id")
    private AssetCondition conditionId;

    @Column(name = "actual_cpu")
    private String actualCpu;

    @Column(name = "actual_ram", length = 100)
    private String actualRam;

    @Column(name = "actual_storage", length = 100)
    private String actualStorage;

    @Column(name = "actual_graphics_card")
    private String actualGraphicsCard;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "asset_id", insertable = false, updatable = false)
    private AssetEntity asset;

    public AssetHardwareDetailsEntity() {}

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public ModelEntity getModel() {
        return model;
    }

    public void setModel(ModelEntity model) {
        this.model = model;
    }

    public LocalDate getWarrantyExpiration() {
        return warrantyExpiration;
    }

    public void setWarrantyExpiration(LocalDate warrantyExpiration) {
        this.warrantyExpiration = warrantyExpiration;
    }

    public AssetCondition getConditionId() {
        return conditionId;
    }

    public void setConditionId(AssetCondition conditionId) {
        this.conditionId = conditionId;
    }

    public String getActualCpu() {
        return actualCpu;
    }

    public void setActualCpu(String actualCpu) {
        this.actualCpu = actualCpu;
    }

    public String getActualRam() {
        return actualRam;
    }

    public void setActualRam(String actualRam) {
        this.actualRam = actualRam;
    }

    public String getActualStorage() {
        return actualStorage;
    }

    public void setActualStorage(String actualStorage) {
        this.actualStorage = actualStorage;
    }

    public String getActualGraphicsCard() {
        return actualGraphicsCard;
    }

    public void setActualGraphicsCard(String actualGraphicsCard) {
        this.actualGraphicsCard = actualGraphicsCard;
    }

    public AssetEntity getAsset() {
        return asset;
    }

    public void setAsset(AssetEntity asset) {
        this.asset = asset;
    }
}
