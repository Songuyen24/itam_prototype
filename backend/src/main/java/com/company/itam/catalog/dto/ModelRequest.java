package com.company.itam.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ModelRequest {
    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String name;

    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String brand;

    @NotNull(message = "{validation.required}")
    private Long typeId;

    @Size(max = 255, message = "{validation.size}")
    private String defaultCpu;

    @Size(max = 100, message = "{validation.size}")
    private String defaultRam;

    @Size(max = 100, message = "{validation.size}")
    private String defaultStorage;

    @Size(max = 255, message = "{validation.size}")
    private String defaultGraphicsCard;

    private Boolean isActive = true;

    public ModelRequest() {}

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
}
