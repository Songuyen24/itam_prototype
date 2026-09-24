package com.company.itam.department.controller;

import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.department.dto.DepartmentRequest;
import com.company.itam.department.dto.DepartmentResponse;
import com.company.itam.department.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DepartmentResponse>>> getDepartments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<DepartmentResponse> result = departmentService.getDepartments(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentById(@PathVariable Long id) {
        DepartmentResponse result = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse result = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo phòng ban thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse result = departmentService.updateDepartment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phòng ban thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa phòng ban thành công", null));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<DepartmentResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        DepartmentResponse result = departmentService.toggleActive(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }
}
