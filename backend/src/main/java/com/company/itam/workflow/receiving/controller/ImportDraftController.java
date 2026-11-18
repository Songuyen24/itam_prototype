package com.company.itam.workflow.receiving.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import com.company.itam.asset.dto.request.CreateHardwareAssetRequest;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.importbatch.dto.request.ImportConfirmRequest;
import com.company.itam.importbatch.service.AssetImportService;
import com.company.itam.publication.service.TransactionPublicationService;
import com.company.itam.workflow.receiving.service.ImportDraftService;

@RestController
@RequestMapping("/v1/import-drafts")
@PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF')")
public class ImportDraftController {
    @Autowired
    private AssetImportService excel;

    private final ImportDraftService service;
    private final MessageHelper messages;
    private final TransactionPublicationService publications;

    public ImportDraftController(
            ImportDraftService service, MessageHelper messages, TransactionPublicationService publications) {
        this.service = service;
        this.messages = messages;
        this.publications = publications;
    }

    @GetMapping("/excel/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=itam_receiving.xlsx")
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(excel.getTemplate());
    }

    @PostMapping(value = "/excel/preview", consumes = "multipart/form-data")
    public Object preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(excel.preview(file));
    }

    @PostMapping(value = "/pdf/preview", consumes = "multipart/form-data")
    public Object previewPdf(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(excel.previewPdf(file));
    }

    @PostMapping("/validate")
    public Object validateRows(@Valid @RequestBody ImportConfirmRequest request) {
        return ApiResponse.success(excel.previewRows(request.getFileName(), request.getValidRows()));
    }

    @PostMapping("/{id}/excel/confirm")
    public Object importExcel(
            @PathVariable Long id,
            @RequestParam Long expectedVersion,
            @Valid @RequestBody ImportConfirmRequest request) {
        return ApiResponse.success(excel.confirmReceiving(id, expectedVersion, request));
    }

    public record Create(@Size(max = 4000) String notes, Long sourceId) {}

    public record Change(
            @NotNull(message = "{validation.required}") Long expectedVersion,
            @Size(max = 4000, message = "{validation.size}") String notes) {}

    public record Process(
            @NotNull(message = "{validation.required}") Long expectedVersion,
            @NotNull(message = "{validation.required}") Integer expectedSubmissionRevision,
            @Size(max = 4000, message = "{validation.size}") String reason) {}

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public Object create(@Valid @RequestBody Create request) {
        return ApiResponse.success(
                messages.getMessage("IMPORT_SAVED"), service.create(request.notes(), request.sourceId()));
    }

    @GetMapping("/options")
    public Object options() {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), service.options());
    }

    @PutMapping("/{id}")
    public Object edit(@PathVariable Long id, @Valid @RequestBody Change request) {
        return ApiResponse.success(
                messages.getMessage("IMPORT_SAVED"),
                service.updateNotes(id, request.expectedVersion(), request.notes()));
    }

    @PostMapping("/{id}/hardware")
    public Object hardware(
            @PathVariable Long id,
            @RequestParam Long expectedVersion,
            @Valid @RequestBody CreateHardwareAssetRequest request) {
        return ApiResponse.success(
                messages.getMessage("IMPORT_SAVED"), service.addHardware(id, expectedVersion, request));
    }

    @PostMapping("/{id}/submit")
    public Object submit(@PathVariable Long id, @Valid @RequestBody Change request) {
        var result = service.submit(id, request.expectedVersion());
        publications.afterCommit(id, () -> publications.onImportSubmitted(id, result.submittedRevision()));
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), result);
    }

    @PostMapping("/{id}/withdraw")
    public Object withdraw(@PathVariable Long id, @Valid @RequestBody Process request) {
        return ApiResponse.success(
                messages.getMessage("IMPORT_SAVED"),
                service.withdraw(id, request.expectedVersion(), request.expectedSubmissionRevision()));
    }

    @PostMapping("/{id}/cancel")
    public Object cancel(@PathVariable Long id, @Valid @RequestBody Change request) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), service.cancel(id, request.expectedVersion()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    @PostMapping("/{id}/approve")
    public Object approve(@PathVariable Long id, @Valid @RequestBody Process request) {
        var result = service.process(id, request.expectedVersion(), request.expectedSubmissionRevision(), true, null);
        publications.afterCommit(id, () -> publications.onImportProcessed(id, true));
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), result);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    @PostMapping("/{id}/reject")
    public Object reject(@PathVariable Long id, @Valid @RequestBody Process request) {
        var result = service.process(
                id, request.expectedVersion(), request.expectedSubmissionRevision(), false, request.reason());
        publications.afterCommit(id, () -> publications.onImportProcessed(id, false));
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), result);
    }

    @PutMapping("/{id}/assets/{assetId}")
    public Object editAsset(
            @PathVariable Long id,
            @PathVariable Long assetId,
            @RequestParam Long expectedVersion,
            @Valid @RequestBody CreateHardwareAssetRequest request) {
        return ApiResponse.success(
                messages.getMessage("IMPORT_SAVED"), service.editLine(id, expectedVersion, assetId, request));
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    public Object removeAsset(@PathVariable Long id, @PathVariable Long assetId, @RequestParam Long expectedVersion) {
        return ApiResponse.success(
                messages.getMessage("IMPORT_SAVED"), service.removeLine(id, expectedVersion, assetId));
    }

    @PostMapping("/{id}/assets/{assetId}")
    public Object reuse(@PathVariable Long id, @PathVariable Long assetId, @RequestParam Long expectedVersion) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), service.reuse(id, expectedVersion, assetId));
    }

    @GetMapping("/candidates")
    public Object candidates(
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), service.candidates(keyword, page));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/reference-data")
    public Object references() {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), service.referenceData());
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/{id}/events")
    public Object events(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), service.events(id));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/{id}/content")
    public Object content(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), service.content(id));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','PUR_STAFF','IT_STAFF')")
    @GetMapping("/{id}/revisions")
    public Object revisions(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("IMPORT_SAVED"), service.revisions(id));
    }
}
