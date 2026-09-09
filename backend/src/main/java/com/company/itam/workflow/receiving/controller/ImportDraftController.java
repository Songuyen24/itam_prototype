package com.company.itam.workflow.receiving.controller;

import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.workflow.receiving.service.ImportDraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/import-drafts")
@PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF')")
public class ImportDraftController {
    private final ImportDraftService service;
    public ImportDraftController(ImportDraftService service) { this.service=service; }
    public record Create(@Size(max=4000) String notes, Long sourceId) {}
    public record Change(@NotNull Long expectedVersion, @Size(max=4000) String notes) {}
    @PostMapping public Object create(@Valid @RequestBody Create request) {
        return ApiResponse.success("OK",service.create(request.notes(),request.sourceId()));
    }
    @GetMapping("/options") public Object options() { return ApiResponse.success("OK",service.options()); }
    @PutMapping("/{id}") public Object edit(@PathVariable Long id,@Valid @RequestBody Change request) {
        return ApiResponse.success("OK",service.updateNotes(id,request.expectedVersion(),request.notes()));
    }
    @PostMapping("/{id}/hardware") public Object hardware(@PathVariable Long id,@RequestParam Long expectedVersion,
            @Valid @RequestBody CreateHardwareAssetRequest request) {
        return ApiResponse.success("OK",service.addHardware(id,expectedVersion,request));
    }
    @PostMapping("/{id}/submit") public Object submit(@PathVariable Long id,@Valid @RequestBody Change request) {
        return ApiResponse.success("OK",service.submit(id,request.expectedVersion()));
    }
    @PostMapping("/{id}/withdraw") public Object withdraw(@PathVariable Long id,@Valid @RequestBody Change request) {
        return ApiResponse.success("OK",service.withdraw(id,request.expectedVersion()));
    }
    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/{id}/content") public Object content(@PathVariable Long id) { return ApiResponse.success("OK",service.content(id)); }
    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/{id}/revisions") public Object revisions(@PathVariable Long id) { return ApiResponse.success("OK",service.revisions(id)); }
}
