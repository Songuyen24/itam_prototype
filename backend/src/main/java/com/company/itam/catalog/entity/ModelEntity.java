package com.company.itam.catalog.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "models")
public class ModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "brand")
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
    private AssetTypeEntity type;

    @Column(name = "default_cpu")
    private String defaultCpu;

    @Column(name = "default_ram", length = 100)
    private String defaultRam;

    @Column(name = "default_storage", length = 100)
    private String defaultStorage;

    @Column(name = "default_graphics_card")
    private String defaultGraphicsCard;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public ModelEntity() {}

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public AssetTypeEntity getType() {
        return type;
    }

    public void setType(AssetTypeEntity type) {
        this.type = type;
    }

    public String getDefaultCpu() {
        return defaultCpu;
    }

    public void setDefaultCpu(String defaultCpu) {
        this.defaultCpu = defaultCpu;
    }

    public String getDefaultRam() {
        return defaultRam;
    }

    public void setDefaultRam(String defaultRam) {
        this.defaultRam = defaultRam;
    }

    public String getDefaultStorage() {
        return defaultStorage;
    }

    public void setDefaultStorage(String defaultStorage) {
        this.defaultStorage = defaultStorage;
    }

    public String getDefaultGraphicsCard() {
        return defaultGraphicsCard;
    }

    public void setDefaultGraphicsCard(String defaultGraphicsCard) {
        this.defaultGraphicsCard = defaultGraphicsCard;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
