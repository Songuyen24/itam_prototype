package com.company.itam.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ModelRequest {
    @NotBlank(message = "Tên model không được để trống")
    @Size(max = 255, message = "Tên model tối đa 255 ký tự")
    private String name;

    @NotBlank(message = "Thương hiệu không được để trống")
    @Size(max = 255, message = "Thương hiệu tối đa 255 ký tự")
    private String brand;

    @NotNull(message = "Loại tài sản không được để trống")
    private Long typeId;

    @Size(max = 255, message = "CPU mặc định tối đa 255 ký tự")
    private String defaultCpu;

    @Size(max = 100, message = "RAM mặc định tối đa 100 ký tự")
    private String defaultRam;

    @Size(max = 100, message = "Ổ cứng mặc định tối đa 100 ký tự")
    private String defaultStorage;

    @Size(max = 255, message = "Card đồ họa mặc định tối đa 255 ký tự")
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
