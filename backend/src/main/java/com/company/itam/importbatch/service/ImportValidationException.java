package com.company.itam.importbatch.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.importbatch.dto.response.ImportRowDetailResponse;
import org.springframework.http.HttpStatus;
import java.util.List;

public class ImportValidationException extends AppException {
    private final List<ImportRowDetailResponse> rows;
    public ImportValidationException(List<ImportRowDetailResponse> rows) {
        super(HttpStatus.BAD_REQUEST, "NO_VALID_ROWS", "NO_VALID_ROWS");
        this.rows = rows;
    }
    public List<ImportRowDetailResponse> getRows() { return rows; }
}
