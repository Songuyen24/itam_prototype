package com.company.itam.catalog.controller;

import com.company.itam.catalog.dto.AssetStatusRequest;
import com.company.itam.catalog.dto.AssetStatusResponse;
import com.company.itam.catalog.service.CatalogService;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/asset-statuses")
public class AssetStatusController {

    private final CatalogService catalogService;

    public AssetStatusController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AssetStatusResponse>>> getStatuses(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<AssetStatusResponse> result = catalogService.getAssetStatuses(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetStatusResponse>> getStatusById(@PathVariable Long id) {
        AssetStatusResponse result = catalogService.getAssetStatusById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PostMapping
    public ResponseEntity<ApiResponse<AssetStatusResponse>> createStatus(@Valid @RequestBody AssetStatusRequest request) {
        AssetStatusResponse result = catalogService.createAssetStatus(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo trạng thái thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetStatusResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AssetStatusRequest request) {
        AssetStatusResponse result = catalogService.updateAssetStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStatus(@PathVariable Long id) {
        catalogService.deleteAssetStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa trạng thái thành công", null));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<AssetStatusResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        AssetStatusResponse result = catalogService.toggleActiveStatus(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
