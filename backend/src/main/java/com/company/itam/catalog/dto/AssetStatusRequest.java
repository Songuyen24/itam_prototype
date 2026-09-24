package com.company.itam.catalog.dto;

import com.company.itam.common.enums.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AssetStatusRequest {
    @NotNull(message = "{validation.required}")
    private AssetStatus code;

    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String name;

    private Boolean isActive = true;

    public AssetStatusRequest() {}

    public AssetStatusRequest(AssetStatus code, String name, Boolean isActive) {
        this.code = code;
        this.name = name;
        this.isActive = isActive;
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
