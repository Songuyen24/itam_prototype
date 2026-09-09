package com.company.itam.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AssetTypeRequest {
    @NotBlank(message = "{validation.required}")
    @Size(max = 50, message = "{validation.size}")
    private String code;

    @NotNull(message = "{validation.required}")
    private Long categoryId;

    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String name;

    private Boolean isActive = true;

    public AssetTypeRequest() {}

    public AssetTypeRequest(String code, Long categoryId, String name, Boolean isActive) {
        this.code = code;
        this.categoryId = categoryId;
        this.name = name;
        this.isActive = isActive;
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
