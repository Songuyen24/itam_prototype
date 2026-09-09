package com.company.itam.document.controller;

import com.company.itam.common.enums.DocumentType;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.document.dto.DocumentDownload;
import com.company.itam.document.dto.DocumentResponse;
import com.company.itam.document.service.DocumentService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/v1/documents")
public class DocumentController {

    private static final Set<String> DOWNLOAD_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

    private final DocumentService documentService;
    private final MessageHelper messageHelper;

    public DocumentController(DocumentService documentService, MessageHelper messageHelper) {
        this.documentService = documentService;
        this.messageHelper = messageHelper;
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'PUR_STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DocumentResponse>>> getDocuments(
            @RequestParam Long transactionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(messageHelper.getMessage("DOCUMENT_LIST_SUCCESS"),
                documentService.getDocuments(transactionId, page, size)));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'PUR_STAFF')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(messageHelper.getMessage("DOCUMENT_GET_SUCCESS"),
                documentService.getDocument(id)));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'PUR_STAFF', 'USER')")
    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long id) {
        DocumentDownload download = documentService.download(id);
        DocumentResponse document = download.document();
        String filename = document.originalFileName().replaceAll("[\\p{Cntrl}/\\\\]", "_");
        String mimeType = document.mimeType();
        MediaType contentType = mimeType != null && DOWNLOAD_TYPES.contains(mimeType)
                ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(download.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(download.content()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'PUR_STAFF')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestParam MultipartFile file,
            @RequestParam Long transactionId,
            @RequestParam DocumentType documentType,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) Long expectedVersion) {
        return ResponseEntity.ok(ApiResponse.success(messageHelper.getMessage("DOCUMENT_GET_SUCCESS"),
                documentService.upload(file, transactionId, documentType, assetId, expectedVersion)));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'PUR_STAFF')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(required=false) Long transactionId,
            @RequestParam(required=false) Long expectedVersion) {
        documentService.delete(id, transactionId, expectedVersion);
        return ResponseEntity.noContent().build();
    }
}
