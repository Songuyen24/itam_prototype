package com.company.itam.catalog.dto;

import java.time.Instant;

public class ModelResponse {
    private Long modelId;
    private String name;
    private String brand;
    private Long typeId;
    private String typeName;
    private String defaultCpu;
    private String defaultRam;
    private String defaultStorage;
    private String defaultGraphicsCard;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public ModelResponse() {}

    public ModelResponse(Long modelId, String name, String brand, Long typeId, String typeName,
                         String defaultCpu, String defaultRam, String defaultStorage, String defaultGraphicsCard,
                         Boolean isActive, Instant createdAt, Instant updatedAt) {
        this.modelId = modelId;
        this.name = name;
        this.brand = brand;
        this.typeId = typeId;
        this.typeName = typeName;
        this.defaultCpu = defaultCpu;
        this.defaultRam = defaultRam;
        this.defaultStorage = defaultStorage;
        this.defaultGraphicsCard = defaultGraphicsCard;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

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

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
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
