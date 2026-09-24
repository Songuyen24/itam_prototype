package com.company.itam.importbatch.dto.response;

import java.util.ArrayList;
import java.util.List;

public class ImportBatchDetailResponse extends ImportBatchResponse {

    private List<ImportRowDetailResponse> rows = new ArrayList<>();

    public ImportBatchDetailResponse() {}

    public List<ImportRowDetailResponse> getRows() {
        return rows;
    }

    public void setRows(List<ImportRowDetailResponse> rows) {
        this.rows = rows;
    }
}
