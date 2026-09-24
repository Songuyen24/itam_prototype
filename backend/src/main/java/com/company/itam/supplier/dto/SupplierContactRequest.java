package com.company.itam.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SupplierContactRequest {
    @NotBlank(message = "{validation.required}")
    @Size(max = 255, message = "{validation.size}")
    private String name;

    @Size(max = 255, message = "{validation.size}")
    private String position;

    @Size(max = 50, message = "{validation.size}")
    private String phone;

    @Email(message = "{validation.email}")
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
