package com.company.itam.asset.entity;

import com.company.itam.catalog.entity.SoftwareCatalogEntity;
import com.company.itam.catalog.entity.SoftwareLicenseTypeEntity;
import com.company.itam.common.enums.LicenseType;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "asset_software_details")
public class AssetSoftwareDetailsEntity {

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "software_catalog_id", nullable = false)
    private SoftwareCatalogEntity softwareCatalog;

    @Column(name = "license_key", length = 500)
    private String licenseKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type_id")
    private LicenseType licenseTypeId;

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "asset_id", insertable = false, updatable = false)
    private AssetEntity asset;

    public AssetSoftwareDetailsEntity() {}

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public SoftwareCatalogEntity getSoftwareCatalog() {
        return softwareCatalog;
    }

    public void setSoftwareCatalog(SoftwareCatalogEntity softwareCatalog) {
        this.softwareCatalog = softwareCatalog;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey;
    }

    public LicenseType getLicenseTypeId() {
        return licenseTypeId;
    }

    public void setLicenseTypeId(LicenseType licenseTypeId) {
        this.licenseTypeId = licenseTypeId;
    }

    public Integer getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(Integer seatCount) {
        this.seatCount = seatCount;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public AssetEntity getAsset() {
        return asset;
    }

    public void setAsset(AssetEntity asset) {
        this.asset = asset;
    }
}
