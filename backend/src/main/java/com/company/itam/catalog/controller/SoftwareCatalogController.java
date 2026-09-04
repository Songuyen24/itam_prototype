package com.company.itam.catalog.controller;

import com.company.itam.catalog.dto.SoftwareCatalogRequest;
import com.company.itam.catalog.dto.SoftwareCatalogResponse;
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
@RequestMapping("/v1/software-catalog")
public class SoftwareCatalogController {

    private final CatalogService catalogService;

    public SoftwareCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SoftwareCatalogResponse>>> getSoftwareCatalog(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<SoftwareCatalogResponse> result = catalogService.getSoftwareCatalog(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SoftwareCatalogResponse>> getSoftwareCatalogById(@PathVariable Long id) {
        SoftwareCatalogResponse result = catalogService.getSoftwareCatalogById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PostMapping
    public ResponseEntity<ApiResponse<SoftwareCatalogResponse>> createSoftwareCatalog(@Valid @RequestBody SoftwareCatalogRequest request) {
        SoftwareCatalogResponse result = catalogService.createSoftwareCatalog(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo phần mềm thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SoftwareCatalogResponse>> updateSoftwareCatalog(
            @PathVariable Long id,
            @Valid @RequestBody SoftwareCatalogRequest request) {
        SoftwareCatalogResponse result = catalogService.updateSoftwareCatalog(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phần mềm thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSoftwareCatalog(@PathVariable Long id) {
        catalogService.deleteSoftwareCatalog(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa phần mềm thành công", null));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<SoftwareCatalogResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        SoftwareCatalogResponse result = catalogService.toggleActiveSoftware(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
