package com.company.itam.catalog.dto;

public class LicenseTermTypeResponse {
    private Long id;
    private String code;
    private String name;
    private Boolean active;

    public LicenseTermTypeResponse() {}

    public LicenseTermTypeResponse(Long id, String code, String name, Boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
