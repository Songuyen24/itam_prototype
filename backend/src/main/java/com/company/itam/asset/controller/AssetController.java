package com.company.itam.asset.controller;

import com.company.itam.asset.dto.request.AssetSearchCriteria;
import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.asset.dto.request.UpdateHardwareAssetRequest;
import com.company.itam.asset.dto.request.ValidateUniquenessRequest;
import com.company.itam.asset.dto.response.AssetDetailResponse;
import com.company.itam.asset.dto.response.AssetResponse;
import com.company.itam.asset.dto.response.UniquenessValidationResponse;
import com.company.itam.asset.service.AssetService;
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
@RequestMapping("/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AssetResponse>>> getAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetTag,
            @RequestParam(required = false) String serialNumber,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long statusId,
            @RequestParam(required = false) Long conditionId,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long assignedTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        AssetSearchCriteria criteria = new AssetSearchCriteria();
        criteria.setKeyword(keyword);
        criteria.setAssetTag(assetTag);
        criteria.setSerialNumber(serialNumber);
        criteria.setTypeId(typeId);
        criteria.setCategoryId(categoryId);
        criteria.setStatusId(statusId);
        criteria.setConditionId(conditionId);
        criteria.setModelId(modelId);
        criteria.setDepartmentId(departmentId);
        criteria.setLocationId(locationId);
        criteria.setSupplierId(supplierId);
        criteria.setAssignedTo(assignedTo);

        PageResponse<AssetResponse> result = assetService.getAssets(criteria, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDetailResponse>> getAssetById(@PathVariable Long id) {
        AssetDetailResponse result = assetService.getAssetById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AssetDetailResponse>> createHardwareAsset(
            @Valid @RequestBody CreateHardwareAssetRequest request) {
        AssetDetailResponse result = assetService.createHardwareAsset(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài sản phần cứng thành công", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDetailResponse>> updateHardwareAsset(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHardwareAssetRequest request) {
        AssetDetailResponse result = assetService.updateHardwareAsset(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tài sản phần cứng thành công", result));
    }

    @PostMapping("/validate-uniqueness")
    public ResponseEntity<ApiResponse<UniquenessValidationResponse>> validateUniqueness(
            @RequestBody ValidateUniquenessRequest request) {
        UniquenessValidationResponse result = assetService.validateUniqueness(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
