package com.company.itam.supplier.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.supplier.dto.SupplierContactRequest;
import com.company.itam.supplier.dto.SupplierContactResponse;
import com.company.itam.supplier.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/supplier-contacts")
public class SupplierContactController {

    private final SupplierService supplierService;

    public SupplierContactController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierContactResponse>> updateContact(
            @PathVariable Long id,
            @Valid @RequestBody SupplierContactRequest request) {
        SupplierContactResponse result = supplierService.updateContact(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật người liên hệ thành công", result));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable Long id) {
        supplierService.deleteContact(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa người liên hệ thành công", null));
    }
}
