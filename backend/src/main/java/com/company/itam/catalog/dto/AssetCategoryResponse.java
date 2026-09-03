package com.company.itam.catalog.dto;

import com.company.itam.common.enums.AssetCategory;

public class AssetCategoryResponse {
    private Long categoryId;
    private AssetCategory code;
    private String name;
    private Boolean isActive;

    public AssetCategoryResponse() {}

    public AssetCategoryResponse(Long categoryId, AssetCategory code, String name, Boolean isActive) {
        this.categoryId = categoryId;
        this.code = code;
        this.name = name;
        this.isActive = isActive;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public AssetCategory getCode() {
        return code;
    }

    public void setCode(AssetCategory code) {
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
