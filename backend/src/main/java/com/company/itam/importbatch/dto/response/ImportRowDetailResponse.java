package com.company.itam.importbatch.dto.response;

import com.company.itam.common.enums.ValidationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImportRowDetailResponse {

    private Integer rowNumber;
    private ValidationStatus validationStatus;
    private String errorMessage;
    private List<ImportRowErrorResponse> errors = new ArrayList<>();
    private Map<String, Object> rawData;
    private Long assetId;

    public ImportRowDetailResponse() {}

    public ImportRowDetailResponse(Integer rowNumber, ValidationStatus validationStatus, String errorMessage,
                                   List<ImportRowErrorResponse> errors, Map<String, Object> rawData) {
        this.rowNumber = rowNumber;
        this.validationStatus = validationStatus;
        this.errorMessage = errorMessage;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.rawData = rawData;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ImportRowErrorResponse> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportRowErrorResponse> errors) {
        this.errors = errors;
    }

    public Map<String, Object> getRawData() {
        return rawData;
    }

    public void setRawData(Map<String, Object> rawData) {
        this.rawData = rawData;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }
}
