package com.company.itam.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SupplierContactRequest {
    @NotBlank(message = "Tên người liên hệ không được để trống")
    @Size(max = 255, message = "Tên người liên hệ tối đa 255 ký tự")
    private String name;

    @Size(max = 255, message = "Chức vụ tối đa 255 ký tự")
    private String position;

    @Size(max = 50, message = "Số điện thoại tối đa 50 ký tự")
    private String phone;

    @Email(message = "Email không đúng định dạng")
    private String email;

    public SupplierContactRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
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
}
