package com.company.itam.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LicenseAssignmentTypeRequest {
    @NotBlank(message = "{validation.required}")
    @Size(max = 50, message = "{validation.size}")
    private String code;

    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String name;

    private Boolean active = true;

    public LicenseAssignmentTypeRequest() {}

    public LicenseAssignmentTypeRequest(String code, String name, Boolean active) {
        this.code = code;
        this.name = name;
        this.active = active;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
