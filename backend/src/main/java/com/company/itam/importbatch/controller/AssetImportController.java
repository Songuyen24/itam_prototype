package com.company.itam.importbatch.controller;

import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.importbatch.dto.request.ImportConfirmRequest;
import com.company.itam.importbatch.dto.response.*;
import com.company.itam.importbatch.service.AssetImportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/asset-imports")
public class AssetImportController {

    private final AssetImportService assetImportService;

    public AssetImportController(AssetImportService assetImportService) {
        this.assetImportService = assetImportService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> getTemplate() {
        byte[] content = assetImportService.getTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"itam_asset_import_template.xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(content);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportPreviewResponse>> preview(
            @RequestParam("file") MultipartFile file) {
        ImportPreviewResponse response = assetImportService.preview(file);
        return ResponseEntity.ok(ApiResponse.success("Kiểm tra dữ liệu file Excel hoàn tất", response));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<ImportBatchResponse>> confirmImport(
            @Valid @RequestBody ImportConfirmRequest request) {
        ImportBatchResponse response = assetImportService.confirmImport(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Nhập tài sản từ Excel thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ImportBatchResponse>>> getImportBatches(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ImportBatchResponse> response = assetImportService.getImportBatches(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ImportBatchDetailResponse>> getImportBatchById(@PathVariable Long id) {
        ImportBatchDetailResponse response = assetImportService.getImportBatchById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/errors")
    public ResponseEntity<ApiResponse<List<ImportRowDetailResponse>>> getImportBatchErrors(@PathVariable Long id) {
        List<ImportRowDetailResponse> response = assetImportService.getImportBatchErrors(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
