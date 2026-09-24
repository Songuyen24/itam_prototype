package com.company.itam.importbatch.dto.response;

import com.company.itam.common.enums.ImportBatchStatus;

import java.time.Instant;

public class ImportBatchResponse {

    private Long importBatchId;
    private String fileName;
    private ImportBatchStatus status;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private Integer duplicateRows;
    private Integer importedRows;
    private Long uploadedByUserId;
    private String uploadedByFullName;
    private Instant createdAt;
    private Instant completedAt;

    public ImportBatchResponse() {}

    public Long getImportBatchId() {
        return importBatchId;
    }

    public void setImportBatchId(Long importBatchId) {
        this.importBatchId = importBatchId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public void setStatus(ImportBatchStatus status) {
        this.status = status;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getValidRows() {
        return validRows;
    }

    public void setValidRows(Integer validRows) {
        this.validRows = validRows;
    }

    public Integer getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(Integer invalidRows) {
        this.invalidRows = invalidRows;
    }

    public Integer getDuplicateRows() {
        return duplicateRows;
    }

    public void setDuplicateRows(Integer duplicateRows) {
        this.duplicateRows = duplicateRows;
    }

    public Integer getImportedRows() {
        return importedRows;
    }

    public void setImportedRows(Integer importedRows) {
        this.importedRows = importedRows;
    }

    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }

    public void setUploadedByUserId(Long uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    public String getUploadedByFullName() {
        return uploadedByFullName;
    }

    public void setUploadedByFullName(String uploadedByFullName) {
        this.uploadedByFullName = uploadedByFullName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
