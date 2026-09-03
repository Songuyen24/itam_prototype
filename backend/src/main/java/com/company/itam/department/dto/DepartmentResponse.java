package com.company.itam.department.dto;

import java.time.Instant;

public class DepartmentResponse {
    private Long departmentId;
    private String code;
    private String name;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public DepartmentResponse() {}

    public DepartmentResponse(Long departmentId, String code, String name, Boolean isActive, Instant createdAt, Instant updatedAt) {
        this.departmentId = departmentId;
        this.code = code;
        this.name = name;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
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
