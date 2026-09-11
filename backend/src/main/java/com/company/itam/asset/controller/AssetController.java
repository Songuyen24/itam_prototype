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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final com.company.itam.common.util.MessageHelper messages;

    public AssetController(AssetService assetService, com.company.itam.common.util.MessageHelper messages) {
        this.messages = messages;
        this.assetService = assetService;
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
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

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping("/recovery-candidates")
    public ResponseEntity<ApiResponse<PageResponse<AssetResponse>>> getRecoveryCandidates(
            @RequestParam Long userId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<AssetResponse> result = assetService.getRecoveryCandidates(userId, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'USER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDetailResponse>> getAssetById(@PathVariable Long id) {
        AssetDetailResponse result = assetService.getAssetById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PostMapping
    public ResponseEntity<ApiResponse<AssetDetailResponse>> createHardwareAsset(
            @Valid @RequestBody CreateHardwareAssetRequest request) {
        AssetDetailResponse result = assetService.createHardwareAsset(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messages.getMessage("ASSET_CREATE_SUCCESS"), result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDetailResponse>> updateHardwareAsset(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHardwareAssetRequest request) {
        AssetDetailResponse result = assetService.updateHardwareAsset(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.getMessage("ASSET_UPDATE_SUCCESS"), result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PostMapping("/validate-uniqueness")
    public ResponseEntity<ApiResponse<UniquenessValidationResponse>> validateUniqueness(
            @RequestBody ValidateUniquenessRequest request) {
        UniquenessValidationResponse result = assetService.validateUniqueness(request);
        result.setAssetTagMessage(messages.getMessage(result.isAssetTagAvailable()?"ASSET_TAG_AVAILABLE":"DUPLICATE_ASSET_TAG"));
        result.setSerialNumberMessage(messages.getMessage(result.isSerialNumberAvailable()?"ASSET_SERIAL_AVAILABLE":"DUPLICATE_SERIAL_NUMBER"));
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
