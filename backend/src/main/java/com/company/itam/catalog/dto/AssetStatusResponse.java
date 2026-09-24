package com.company.itam.catalog.dto;

import com.company.itam.common.enums.AssetStatus;

public class AssetStatusResponse {
    private Long statusId;
    private AssetStatus code;
    private String name;
    private Boolean isActive;

    public AssetStatusResponse() {}

    public AssetStatusResponse(Long statusId, AssetStatus code, String name, Boolean isActive) {
        this.statusId = statusId;
        this.code = code;
        this.name = name;
        this.isActive = isActive;
    }

    public Long getStatusId() {
        return statusId;
    }

    public void setStatusId(Long statusId) {
        this.statusId = statusId;
    }

    public AssetStatus getCode() {
        return code;
    }

    public void setCode(AssetStatus code) {
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
