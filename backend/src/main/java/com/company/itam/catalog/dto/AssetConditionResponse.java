package com.company.itam.catalog.dto;

import com.company.itam.common.enums.AssetCondition;

public class AssetConditionResponse {
    private Long conditionId;
    private AssetCondition code;
    private String name;
    private Boolean isActive;

    public AssetConditionResponse() {}

    public AssetConditionResponse(Long conditionId, AssetCondition code, String name, Boolean isActive) {
        this.conditionId = conditionId;
        this.code = code;
        this.name = name;
        this.isActive = isActive;
    }

    public Long getConditionId() {
        return conditionId;
    }

    public void setConditionId(Long conditionId) {
        this.conditionId = conditionId;
    }

    public AssetCondition getCode() {
        return code;
    }

    public void setCode(AssetCondition code) {
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
