package com.company.itam.catalog.entity;

import com.company.itam.common.enums.LicenseType;
import jakarta.persistence.*;

@Entity
@Table(name = "software_license_types")
public class SoftwareLicenseTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "license_type_id")
    private Long licenseTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private LicenseType code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public SoftwareLicenseTypeEntity() {}

    public Long getLicenseTypeId() {
        return licenseTypeId;
    }

    public void setLicenseTypeId(Long licenseTypeId) {
        this.licenseTypeId = licenseTypeId;
    }

    public LicenseType getCode() {
        return code;
    }

    public void setCode(LicenseType code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
