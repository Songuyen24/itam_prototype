package com.company.itam.catalog.controller;

import com.company.itam.catalog.dto.AssetTypeRequest;
import com.company.itam.catalog.dto.AssetTypeResponse;
import com.company.itam.catalog.service.CatalogService;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/asset-types")
public class AssetTypeController {

    private final CatalogService catalogService;

    public AssetTypeController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AssetTypeResponse>>> getTypes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<AssetTypeResponse> result = catalogService.getAssetTypes(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetTypeResponse>> getTypeById(@PathVariable Long id) {
        AssetTypeResponse result = catalogService.getAssetTypeById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AssetTypeResponse>> createType(@Valid @RequestBody AssetTypeRequest request) {
        AssetTypeResponse result = catalogService.createAssetType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo loại tài sản thành công", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetTypeResponse>> updateType(
            @PathVariable Long id,
            @Valid @RequestBody AssetTypeRequest request) {
        AssetTypeResponse result = catalogService.updateAssetType(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật loại tài sản thành công", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteType(@PathVariable Long id) {
        catalogService.deleteAssetType(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa loại tài sản thành công", null));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<AssetTypeResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        AssetTypeResponse result = catalogService.toggleActiveType(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
