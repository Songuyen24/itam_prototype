package com.company.itam.supplier.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SupplierResponse {
    private Long supplierId;
    private String code;
    private String name;
    private String taxCode;
    private String address;
    private String phone;
    private String email;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private List<SupplierContactResponse> contacts = new ArrayList<>();

    public SupplierResponse() {}

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<SupplierContactResponse> getContacts() {
        return contacts;
    }

    public void setContacts(List<SupplierContactResponse> contacts) {
        this.contacts = contacts;
    }
}
