package com.company.itam.importbatch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public class ImportConfirmRequest {

    @NotBlank(message = "{validation.required}")
    private String fileName;

    @NotEmpty(message = "{validation.required}")
    private List<Map<String, Object>> validRows;

    public ImportConfirmRequest() {}

    public ImportConfirmRequest(String fileName, List<Map<String, Object>> validRows) {
        this.fileName = fileName;
        this.validRows = validRows;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<Map<String, Object>> getValidRows() {
        return validRows;
    }

    public void setValidRows(List<Map<String, Object>> validRows) {
        this.validRows = validRows;
    }
}
