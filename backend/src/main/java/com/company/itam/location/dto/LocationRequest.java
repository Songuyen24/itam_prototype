package com.company.itam.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LocationRequest {
    @NotBlank(message = "Mã vị trí không được để trống")
    @Size(max = 50, message = "Mã vị trí tối đa 50 ký tự")
    private String code;

    @NotBlank(message = "Tên vị trí không được để trống")
    @Size(max = 255, message = "Tên vị trí tối đa 255 ký tự")
    private String name;

    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    private Boolean isActive = true;

    public LocationRequest() {}

    public LocationRequest(String code, String name, String address, Boolean isActive) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.isActive = isActive;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
