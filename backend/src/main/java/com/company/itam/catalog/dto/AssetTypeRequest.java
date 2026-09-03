package com.company.itam.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AssetTypeRequest {
    @NotBlank(message = "Mã loại tài sản không được để trống")
    @Size(max = 50, message = "Mã loại tài sản tối đa 50 ký tự")
    private String code;

    @NotNull(message = "Nhóm tài sản không được để trống")
    private Long categoryId;

    @NotBlank(message = "Tên loại tài sản không được để trống")
    @Size(max = 255, message = "Tên loại tài sản tối đa 255 ký tự")
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
