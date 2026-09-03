package com.company.itam.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SoftwareCatalogRequest {
    @NotBlank(message = "Tên phần mềm không được để trống")
    @Size(max = 255, message = "Tên phần mềm tối đa 255 ký tự")
    private String name;

    @NotBlank(message = "Nhà sản xuất không được để trống")
    @Size(max = 255, message = "Nhà sản xuất tối đa 255 ký tự")
    private String manufacturer;

    @Size(max = 100, message = "Phiên bản tối đa 100 ký tự")
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
