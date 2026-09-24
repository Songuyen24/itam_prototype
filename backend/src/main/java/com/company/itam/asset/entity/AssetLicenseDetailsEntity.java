package com.company.itam.asset.entity;

import com.company.itam.catalog.entity.LicenseAssignmentTypeEntity;
import com.company.itam.catalog.entity.LicenseTermTypeEntity;
import com.company.itam.catalog.entity.SoftwareCatalogEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "asset_license_details")
public class AssetLicenseDetailsEntity {
    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private AssetEntity asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "software_catalog_id", nullable = false)
    private SoftwareCatalogEntity softwareCatalog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_assignment_type_id", nullable = false)
    private LicenseAssignmentTypeEntity assignmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_term_type_id", nullable = false)
    private LicenseTermTypeEntity termType;

    @Column(name = "license_key", length = 500)
    private String licenseKey;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "seat_count", nullable = false)
    private int seatCount = 1;
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int value) { seatCount = value; }

    public Long getAssetId() { return assetId; }
    public AssetEntity getAsset() { return asset; }
    public void setAsset(AssetEntity asset) { this.asset = asset; }
    public SoftwareCatalogEntity getSoftwareCatalog() { return softwareCatalog; }
    public void setSoftwareCatalog(SoftwareCatalogEntity value) { this.softwareCatalog = value; }
    public LicenseAssignmentTypeEntity getAssignmentType() { return assignmentType; }
    public void setAssignmentType(LicenseAssignmentTypeEntity value) { this.assignmentType = value; }
    public LicenseTermTypeEntity getTermType() { return termType; }
    public void setTermType(LicenseTermTypeEntity value) { this.termType = value; }
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}
