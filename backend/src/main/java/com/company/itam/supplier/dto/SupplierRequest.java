package com.company.itam.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SupplierRequest {
    @NotBlank(message = "{validation.required}")
    @Size(max = 50, message = "{validation.size}")
    private String code;

    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String name;

    @Size(max = 100, message = "{validation.size}")
    private String taxCode;

    @Size(max = 500, message = "{validation.size}")
    private String address;

    @Size(max = 50, message = "{validation.size}")
    private String phone;

    @Email(message = "{validation.email}")
    private String email;

    private Boolean isActive = true;

    public SupplierRequest() {}

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

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
