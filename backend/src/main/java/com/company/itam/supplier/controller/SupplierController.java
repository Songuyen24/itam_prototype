package com.company.itam.supplier.controller;

import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.supplier.dto.SupplierContactRequest;
import com.company.itam.supplier.dto.SupplierContactResponse;
import com.company.itam.supplier.dto.SupplierRequest;
import com.company.itam.supplier.dto.SupplierResponse;
import com.company.itam.supplier.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SupplierResponse>>> getSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<SupplierResponse> result = supplierService.getSuppliers(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> getSupplierById(@PathVariable Long id) {
        SupplierResponse result = supplierService.getSupplierById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse result = supplierService.createSupplier(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo nhà cung cấp thành công", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {
        SupplierResponse result = supplierService.updateSupplier(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nhà cung cấp thành công", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa nhà cung cấp thành công", null));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<SupplierResponse>> toggleActive(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active) {
        SupplierResponse result = supplierService.toggleActive(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", result));
    }

    // Contacts
    @GetMapping("/{id}/contacts")
    public ResponseEntity<ApiResponse<List<SupplierContactResponse>>> getContacts(@PathVariable Long id) {
        List<SupplierContactResponse> result = supplierService.getContacts(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/contacts")
    public ResponseEntity<ApiResponse<SupplierContactResponse>> addContact(
            @PathVariable Long id,
            @Valid @RequestBody SupplierContactRequest request) {
        SupplierContactResponse result = supplierService.addContact(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Thêm người liên hệ thành công", result));
    }

    @PutMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<ApiResponse<SupplierContactResponse>> updateContact(
            @PathVariable Long id,
            @PathVariable Long contactId,
            @Valid @RequestBody SupplierContactRequest request) {
        SupplierContactResponse result = supplierService.updateContact(contactId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật người liên hệ thành công", result));
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<ApiResponse<Void>> deleteContact(
            @PathVariable Long id,
            @PathVariable Long contactId) {
        supplierService.deleteContact(contactId);
        return ResponseEntity.ok(ApiResponse.success("Xóa người liên hệ thành công", null));
    }
}
