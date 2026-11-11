package com.company.itam.catalog.controller;

import com.company.itam.catalog.dto.AssetStatusResponse;
import com.company.itam.catalog.service.CatalogService;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
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

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AssetStatusResponse>>> getStatuses(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<AssetStatusResponse> result = catalogService.getAssetStatuses(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetStatusResponse>> getStatusById(@PathVariable Long id) {
        AssetStatusResponse result = catalogService.getAssetStatusById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @RequestMapping(path = {"", "/{id}", "/{id}/active"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<Void> rejectStatusChanges() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

}
