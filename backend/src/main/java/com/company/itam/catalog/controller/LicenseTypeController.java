package com.company.itam.catalog.controller;

import com.company.itam.catalog.dto.LicenseAssignmentTypeRequest;
import com.company.itam.catalog.dto.LicenseAssignmentTypeResponse;
import com.company.itam.catalog.dto.LicenseTermTypeRequest;
import com.company.itam.catalog.dto.LicenseTermTypeResponse;
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
public class LicenseTypeController {

    private final CatalogService catalogService;

    public LicenseTypeController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // ==========================================
    // LICENSE ASSIGNMENT TYPES
    // ==========================================
    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping("/v1/license-assignment-types")
    public ResponseEntity<ApiResponse<PageResponse<LicenseAssignmentTypeResponse>>> getAssignmentTypes(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<LicenseAssignmentTypeResponse> result = catalogService.getLicenseAssignmentTypes(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping("/v1/license-assignment-types/{id}")
    public ResponseEntity<ApiResponse<LicenseAssignmentTypeResponse>> getAssignmentTypeById(@PathVariable Long id) {
        LicenseAssignmentTypeResponse result = catalogService.getLicenseAssignmentTypeById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PostMapping("/v1/license-assignment-types")
    public ResponseEntity<ApiResponse<LicenseAssignmentTypeResponse>> createAssignmentType(
            @Valid @RequestBody LicenseAssignmentTypeRequest request) {
        LicenseAssignmentTypeResponse result = catalogService.createLicenseAssignmentType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo loại gán license thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PutMapping("/v1/license-assignment-types/{id}")
    public ResponseEntity<ApiResponse<LicenseAssignmentTypeResponse>> updateAssignmentType(
            @PathVariable Long id,
            @Valid @RequestBody LicenseAssignmentTypeRequest request) {
        LicenseAssignmentTypeResponse result = catalogService.updateLicenseAssignmentType(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật loại gán license thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @DeleteMapping("/v1/license-assignment-types/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAssignmentType(@PathVariable Long id) {
        catalogService.deleteLicenseAssignmentType(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa loại gán license thành công", null));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PatchMapping("/v1/license-assignment-types/{id}/active")
    public ResponseEntity<ApiResponse<LicenseAssignmentTypeResponse>> toggleActiveAssignmentType(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        LicenseAssignmentTypeResponse result = catalogService.toggleActiveAssignmentType(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }

    // ==========================================
    // LICENSE TERM TYPES
    // ==========================================
    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping("/v1/license-term-types")
    public ResponseEntity<ApiResponse<PageResponse<LicenseTermTypeResponse>>> getTermTypes(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<LicenseTermTypeResponse> result = catalogService.getLicenseTermTypes(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping("/v1/license-term-types/{id}")
    public ResponseEntity<ApiResponse<LicenseTermTypeResponse>> getTermTypeById(@PathVariable Long id) {
        LicenseTermTypeResponse result = catalogService.getLicenseTermTypeById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PostMapping("/v1/license-term-types")
    public ResponseEntity<ApiResponse<LicenseTermTypeResponse>> createTermType(
            @Valid @RequestBody LicenseTermTypeRequest request) {
        LicenseTermTypeResponse result = catalogService.createLicenseTermType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo loại thời hạn license thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PutMapping("/v1/license-term-types/{id}")
    public ResponseEntity<ApiResponse<LicenseTermTypeResponse>> updateTermType(
            @PathVariable Long id,
            @Valid @RequestBody LicenseTermTypeRequest request) {
        LicenseTermTypeResponse result = catalogService.updateLicenseTermType(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật loại thời hạn license thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @DeleteMapping("/v1/license-term-types/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTermType(@PathVariable Long id) {
        catalogService.deleteLicenseTermType(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa loại thời hạn license thành công", null));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PatchMapping("/v1/license-term-types/{id}/active")
    public ResponseEntity<ApiResponse<LicenseTermTypeResponse>> toggleActiveTermType(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        LicenseTermTypeResponse result = catalogService.toggleActiveTermType(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
