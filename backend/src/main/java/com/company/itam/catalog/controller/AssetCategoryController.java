package com.company.itam.catalog.controller;

import com.company.itam.catalog.dto.AssetCategoryRequest;
import com.company.itam.catalog.dto.AssetCategoryResponse;
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
@RequestMapping("/v1/asset-categories")
public class AssetCategoryController {

    private final CatalogService catalogService;

    public AssetCategoryController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AssetCategoryResponse>>> getCategories(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<AssetCategoryResponse> result = catalogService.getAssetCategories(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> getCategoryById(@PathVariable Long id) {
        AssetCategoryResponse result = catalogService.getAssetCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> createCategory(@Valid @RequestBody AssetCategoryRequest request) {
        AssetCategoryResponse result = catalogService.createAssetCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo nhóm tài sản thành công", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody AssetCategoryRequest request) {
        AssetCategoryResponse result = catalogService.updateAssetCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nhóm tài sản thành công", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        catalogService.deleteAssetCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa nhóm tài sản thành công", null));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        AssetCategoryResponse result = catalogService.toggleActiveCategory(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
