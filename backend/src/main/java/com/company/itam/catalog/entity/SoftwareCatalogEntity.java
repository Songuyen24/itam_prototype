package com.company.itam.catalog.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "software_catalog")
public class SoftwareCatalogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "software_catalog_id")
    private Long softwareCatalogId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "version", length = 100)
    private String version;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public SoftwareCatalogEntity() {}

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
}
