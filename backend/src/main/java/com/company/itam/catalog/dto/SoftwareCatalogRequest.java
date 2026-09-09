package com.company.itam.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SoftwareCatalogRequest {
    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String name;

    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String manufacturer;

    @Size(max = 100, message = "{validation.size}")
    private String version;

    private Boolean isActive = true;

    public SoftwareCatalogRequest() {}

    public SoftwareCatalogRequest(String name, String manufacturer, String version, Boolean isActive) {
        this.name = name;
        this.manufacturer = manufacturer;
        this.version = version;
        this.isActive = isActive;
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
}
