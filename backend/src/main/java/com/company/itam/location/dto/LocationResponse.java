package com.company.itam.location.dto;

import java.time.Instant;

public class LocationResponse {
    private Long locationId;
    private String code;
    private String name;
    private String address;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public LocationResponse() {}

    public LocationResponse(Long locationId, String code, String name, String address, Boolean isActive, Instant createdAt, Instant updatedAt) {
        this.locationId = locationId;
        this.code = code;
        this.name = name;
        this.address = address;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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
