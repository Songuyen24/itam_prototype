package com.company.itam.catalog.dto;

import com.company.itam.common.enums.AssetCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AssetCategoryRequest {
    @NotNull(message = "Mã nhóm tài sản không được để trống")
    private AssetCategory code;

    @NotBlank(message = "Tên nhóm tài sản không được để trống")
    @Size(max = 255, message = "Tên nhóm tài sản tối đa 255 ký tự")
    private String name;

    private Boolean isActive = true;

    public AssetCategoryRequest() {}

    public AssetCategoryRequest(AssetCategory code, String name, Boolean isActive) {
        this.code = code;
        this.name = name;
        this.isActive = isActive;
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
