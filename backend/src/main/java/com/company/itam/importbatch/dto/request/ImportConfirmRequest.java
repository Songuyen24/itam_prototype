package com.company.itam.importbatch.dto.request;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class ImportConfirmRequest {

    @Size(max = 500)
    @NotBlank(message = "{validation.required}")
    private String fileName;

    private UUID requestId;

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID value) {
        requestId = value;
    }

    private Long retryBatchId;

    public Long getRetryBatchId() {
        return retryBatchId;
    }

    public void setRetryBatchId(Long value) {
        retryBatchId = value;
    }

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
