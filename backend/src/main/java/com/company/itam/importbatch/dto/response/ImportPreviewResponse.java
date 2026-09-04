package com.company.itam.importbatch.dto.response;

import java.util.ArrayList;
import java.util.List;

public class ImportPreviewResponse {

    private String fileName;
    private Integer totalRows = 0;
    private Integer validRows = 0;
    private Integer invalidRows = 0;
    private Integer duplicateRows = 0;
    private List<ImportRowDetailResponse> rows = new ArrayList<>();

    public ImportPreviewResponse() {}

    public ImportPreviewResponse(String fileName, Integer totalRows, Integer validRows,
                                 Integer invalidRows, Integer duplicateRows,
                                 List<ImportRowDetailResponse> rows) {
        this.fileName = fileName;
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.invalidRows = invalidRows;
        this.duplicateRows = duplicateRows;
        this.rows = rows != null ? rows : new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public List<ImportRowDetailResponse> getRows() {
        return rows;
    }

    public void setRows(List<ImportRowDetailResponse> rows) {
        this.rows = rows;
    }
}
