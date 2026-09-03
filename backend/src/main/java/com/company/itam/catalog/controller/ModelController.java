package com.company.itam.catalog.controller;

import com.company.itam.catalog.dto.ModelRequest;
import com.company.itam.catalog.dto.ModelResponse;
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
@RequestMapping("/v1/models")
public class ModelController {

    private final CatalogService catalogService;

    public ModelController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ModelResponse>>> getModels(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<ModelResponse> result = catalogService.getModels(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ModelResponse>> getModelById(@PathVariable Long id) {
        ModelResponse result = catalogService.getModelById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ModelResponse>> createModel(@Valid @RequestBody ModelRequest request) {
        ModelResponse result = catalogService.createModel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo model thành công", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ModelResponse>> updateModel(
            @PathVariable Long id,
            @Valid @RequestBody ModelRequest request) {
        ModelResponse result = catalogService.updateModel(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật model thành công", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteModel(@PathVariable Long id) {
        catalogService.deleteModel(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa model thành công", null));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<ModelResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        ModelResponse result = catalogService.toggleActiveModel(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
