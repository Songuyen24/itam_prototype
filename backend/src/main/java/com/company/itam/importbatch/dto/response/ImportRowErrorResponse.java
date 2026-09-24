package com.company.itam.importbatch.dto.response;

public class ImportRowErrorResponse {

    private Integer rowNumber;
    private String column;
    private String code;
    private String message;

    public ImportRowErrorResponse() {}

    public ImportRowErrorResponse(Integer rowNumber, String column, String code, String message) {
        this.rowNumber = rowNumber;
        this.column = column;
        this.code = code;
        this.message = message;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
