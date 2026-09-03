package com.company.itam.catalog.dto;

import com.company.itam.common.enums.AssetCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AssetConditionRequest {
    @NotNull(message = "Mã tình trạng không được để trống")
    private AssetCondition code;

    @NotBlank(message = "Tên tình trạng không được để trống")
    @Size(max = 255, message = "Tên tình trạng tối đa 255 ký tự")
    private String name;

    private Boolean isActive = true;

    public AssetConditionRequest() {}

    public AssetConditionRequest(AssetCondition code, String name, Boolean isActive) {
        this.code = code;
        this.name = name;
        this.isActive = isActive;
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
