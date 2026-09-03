package com.company.itam.asset.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateHardwareAssetRequest {

    @NotBlank(message = "Mã tài sản (Asset Tag) không được để trống")
    @Size(max = 100, message = "Mã tài sản không được vượt quá 100 ký tự")
    private String assetTag;

    @NotBlank(message = "Tên tài sản không được để trống")
    @Size(max = 255, message = "Tên tài sản không được vượt quá 255 ký tự")
    private String name;

    @NotNull(message = "Loại tài sản (Type) là bắt buộc")
    private Long typeId;

    private Long statusId;

    private Long departmentId;

    private Long locationId;

    private Long supplierId;

    @Size(max = 100, message = "Số PO không được vượt quá 100 ký tự")
    private String poNumber;

    private LocalDate purchaseDate;

    @DecimalMin(value = "0.0", message = "Giá mua không được âm")
    private BigDecimal purchaseCost = BigDecimal.ZERO;

    private Long assignedToUserId;

    // Hardware specific details
    @Size(max = 255, message = "Serial number không được vượt quá 255 ký tự")
    private String serialNumber;

    private Long modelId;

    private Long conditionId;

    private LocalDate warrantyExpiration;

    @Size(max = 255, message = "Actual CPU không được vượt quá 255 ký tự")
    private String actualCpu;

    @Size(max = 100, message = "Actual RAM không được vượt quá 100 ký tự")
    private String actualRam;

    @Size(max = 100, message = "Actual Storage không được vượt quá 100 ký tự")
    private String actualStorage;

    @Size(max = 255, message = "Actual Graphics Card không được vượt quá 255 ký tự")
    private String actualGraphicsCard;

    public CreateHardwareAssetRequest() {}

    public String getAssetTag() {
        return assetTag;
    }

    public void setAssetTag(String assetTag) {
        this.assetTag = assetTag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public Long getStatusId() {
        return statusId;
    }

    public void setStatusId(Long statusId) {
        this.statusId = statusId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public Long getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(Long assignedToUserId) {
        this.assignedToUserId = assignedToUserId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Long getConditionId() {
        return conditionId;
    }

    public void setConditionId(Long conditionId) {
        this.conditionId = conditionId;
    }

    public LocalDate getWarrantyExpiration() {
        return warrantyExpiration;
    }

    public void setWarrantyExpiration(LocalDate warrantyExpiration) {
        this.warrantyExpiration = warrantyExpiration;
    }

    public String getActualCpu() {
        return actualCpu;
    }

    public void setActualCpu(String actualCpu) {
        this.actualCpu = actualCpu;
    }

    public String getActualRam() {
        return actualRam;
    }

    public void setActualRam(String actualRam) {
        this.actualRam = actualRam;
    }

    public String getActualStorage() {
        return actualStorage;
    }

    public void setActualStorage(String actualStorage) {
        this.actualStorage = actualStorage;
    }

    public String getActualGraphicsCard() {
        return actualGraphicsCard;
    }

    public void setActualGraphicsCard(String actualGraphicsCard) {
        this.actualGraphicsCard = actualGraphicsCard;
    }
}
