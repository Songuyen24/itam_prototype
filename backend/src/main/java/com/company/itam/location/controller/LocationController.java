package com.company.itam.location.controller;

import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.location.dto.LocationRequest;
import com.company.itam.location.dto.LocationResponse;
import com.company.itam.location.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LocationResponse>>> getLocations(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<LocationResponse> result = locationService.getLocations(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocationById(@PathVariable Long id) {
        LocationResponse result = locationService.getLocationById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LocationResponse>> createLocation(@Valid @RequestBody LocationRequest request) {
        LocationResponse result = locationService.createLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo vị trí thành công", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody LocationRequest request) {
        LocationResponse result = locationService.updateLocation(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vị trí thành công", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLocation(@PathVariable Long id) {
        locationService.deleteLocation(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa vị trí thành công", null));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<LocationResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        LocationResponse result = locationService.toggleActive(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
