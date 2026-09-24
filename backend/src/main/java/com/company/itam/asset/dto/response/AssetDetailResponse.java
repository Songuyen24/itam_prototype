package com.company.itam.asset.dto.response;

import java.time.LocalDate;

public class AssetDetailResponse extends AssetResponse {

    private LicenseDetailsResponse license;
    public LicenseDetailsResponse getLicense() { return license; }
    public void setLicense(LicenseDetailsResponse value) { license = value; }
    private LocalDate warrantyExpiration;
    private HardwareConfigDto hardwareConfig;

    private Long createdByUserId;
    private String createdByFullName;

    private Long updatedByUserId;
    private String updatedByFullName;

    public AssetDetailResponse() {}

    public LocalDate getWarrantyExpiration() {
        return warrantyExpiration;
    }

    public void setWarrantyExpiration(LocalDate warrantyExpiration) {
        this.warrantyExpiration = warrantyExpiration;
    }

    public HardwareConfigDto getHardwareConfig() {
        return hardwareConfig;
    }

    public void setHardwareConfig(HardwareConfigDto hardwareConfig) {
        this.hardwareConfig = hardwareConfig;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getCreatedByFullName() {
        return createdByFullName;
    }

    public void setCreatedByFullName(String createdByFullName) {
        this.createdByFullName = createdByFullName;
    }

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(Long updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    public String getUpdatedByFullName() {
        return updatedByFullName;
    }

    public void setUpdatedByFullName(String updatedByFullName) {
        this.updatedByFullName = updatedByFullName;
    }
}
