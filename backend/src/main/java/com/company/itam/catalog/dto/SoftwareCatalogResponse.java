package com.company.itam.catalog.dto;

import java.time.Instant;

public class SoftwareCatalogResponse {
    private Long softwareCatalogId;
    private String name;
    private String manufacturer;
    private String version;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public SoftwareCatalogResponse() {}

    public SoftwareCatalogResponse(Long softwareCatalogId, String name, String manufacturer, String version, Boolean isActive, Instant createdAt, Instant updatedAt) {
        this.softwareCatalogId = softwareCatalogId;
        this.name = name;
        this.manufacturer = manufacturer;
        this.version = version;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getSoftwareCatalogId() {
        return softwareCatalogId;
    }

    public void setSoftwareCatalogId(Long softwareCatalogId) {
        this.softwareCatalogId = softwareCatalogId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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
