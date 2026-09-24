package com.company.itam.catalog.dto;

public class AssetTypeResponse {
    private Long typeId;
    private String code;
    private Long categoryId;
    private String categoryName;
    private String name;
    private Boolean isActive;

    public AssetTypeResponse() {}

    public AssetTypeResponse(Long typeId, String code, Long categoryId, String categoryName, String name, Boolean isActive) {
        this.typeId = typeId;
        this.code = code;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.name = name;
        this.isActive = isActive;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
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
