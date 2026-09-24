package com.company.itam.supplier.dto;

public class SupplierContactResponse {
    private Long contactId;
    private Long supplierId;
    private String name;
    private String position;
    private String phone;
    private String email;

    public SupplierContactResponse() {}

    public SupplierContactResponse(Long contactId, Long supplierId, String name, String position, String phone, String email) {
        this.contactId = contactId;
        this.supplierId = supplierId;
        this.name = name;
        this.position = position;
        this.phone = phone;
        this.email = email;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

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
