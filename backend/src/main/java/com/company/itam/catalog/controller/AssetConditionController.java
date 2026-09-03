package com.company.itam.catalog.controller;

import com.company.itam.catalog.dto.AssetConditionRequest;
import com.company.itam.catalog.dto.AssetConditionResponse;
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
@RequestMapping("/v1/asset-conditions")
public class AssetConditionController {

    private final CatalogService catalogService;

    public AssetConditionController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AssetConditionResponse>>> getConditions(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<AssetConditionResponse> result = catalogService.getAssetConditions(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetConditionResponse>> getConditionById(@PathVariable Long id) {
        AssetConditionResponse result = catalogService.getAssetConditionById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AssetConditionResponse>> createCondition(@Valid @RequestBody AssetConditionRequest request) {
        AssetConditionResponse result = catalogService.createAssetCondition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo tình trạng thành công", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetConditionResponse>> updateCondition(
            @PathVariable Long id,
            @Valid @RequestBody AssetConditionRequest request) {
        AssetConditionResponse result = catalogService.updateAssetCondition(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tình trạng thành công", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCondition(@PathVariable Long id) {
        catalogService.deleteAssetCondition(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa tình trạng thành công", null));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<AssetConditionResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        AssetConditionResponse result = catalogService.toggleActiveCondition(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
