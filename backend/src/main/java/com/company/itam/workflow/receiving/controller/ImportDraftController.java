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
    private final com.company.itam.common.util.MessageHelper messages;
    public ImportDraftController(ImportDraftService service, com.company.itam.common.util.MessageHelper messages) { this.service=service; this.messages=messages; }
    public record Create(@Size(max=4000) String notes, Long sourceId) {}
    public record Change(@NotNull(message="{validation.required}") Long expectedVersion, @Size(max=4000, message="{validation.size}") String notes) {}
    public record Process(@NotNull(message="{validation.required}") Long expectedVersion, @NotNull(message="{validation.required}") Integer expectedSubmissionRevision, @Size(max=4000, message="{validation.size}") String reason) {}
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PostMapping public Object create(@Valid @RequestBody Create request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.create(request.notes(),request.sourceId()));
    }
    @GetMapping("/options") public Object options() { return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.options()); }
    @PutMapping("/{id}") public Object edit(@PathVariable Long id,@Valid @RequestBody Change request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.updateNotes(id,request.expectedVersion(),request.notes()));
    }
    @PostMapping("/{id}/hardware") public Object hardware(@PathVariable Long id,@RequestParam Long expectedVersion,
            @Valid @RequestBody CreateHardwareAssetRequest request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.addHardware(id,expectedVersion,request));
    }
    @PostMapping("/{id}/submit") public Object submit(@PathVariable Long id,@Valid @RequestBody Change request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.submit(id,request.expectedVersion()));
    }
    @PostMapping("/{id}/withdraw") public Object withdraw(@PathVariable Long id,@Valid @RequestBody Process request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.withdraw(id,request.expectedVersion(),request.expectedSubmissionRevision()));
    }
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    @PostMapping("/{id}/approve") public Object approve(@PathVariable Long id,@Valid @RequestBody Process request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.process(id,request.expectedVersion(),request.expectedSubmissionRevision(),true,null));
    }
    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    @PostMapping("/{id}/reject") public Object reject(@PathVariable Long id,@Valid @RequestBody Process request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.process(id,request.expectedVersion(),request.expectedSubmissionRevision(),false,request.reason()));
    }
    @PutMapping("/{id}/assets/{assetId}") public Object editAsset(@PathVariable Long id,@PathVariable Long assetId,@RequestParam Long expectedVersion,@Valid @RequestBody CreateHardwareAssetRequest request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.editLine(id,expectedVersion,assetId,request));
    }
    @DeleteMapping("/{id}/assets/{assetId}") public Object removeAsset(@PathVariable Long id,@PathVariable Long assetId,@RequestParam Long expectedVersion) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.removeLine(id,expectedVersion,assetId));
    }
    @PostMapping("/{id}/assets/{assetId}") public Object reuse(@PathVariable Long id,@PathVariable Long assetId,@RequestParam Long expectedVersion) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.reuse(id,expectedVersion,assetId));
    }
    @GetMapping("/candidates") public Object candidates(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="0") int page) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.candidates(keyword,page));
    }
    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/reference-data") public Object references() { return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.referenceData()); }
    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/{id}/events") public Object events(@PathVariable Long id) { return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.events(id)); }
    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/{id}/content") public Object content(@PathVariable Long id) { return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.content(id)); }
    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/{id}/revisions") public Object revisions(@PathVariable Long id) { return ApiResponse.success(messages.getMessage("IMPORT_SAVED"),service.revisions(id)); }
}
