package com.company.itam.publication.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.publication.dto.PublicationStatusResponse;
import com.company.itam.publication.service.TransactionPublicationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/v1/transactions/{id}")
public class TransactionPublicationController {
    private final TransactionPublicationService service;
    private final MessageHelper messages;

    public TransactionPublicationController(TransactionPublicationService service, MessageHelper messages) {
        this.service = service; this.messages = messages;
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF','PUR_STAFF')")
    @GetMapping("/publication")
    public ApiResponse<PublicationStatusResponse> status(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("PUBLICATION_READ"), service.status(id));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF','PUR_STAFF','USER')")
    @GetMapping("/pdf")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long id) {
        var download = service.latestPdf(id);
        String fileName = download.document().originalFileName().replaceAll("[\\p{Cntrl}/\\\\]", "_");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(download.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(new ByteArrayResource(download.content()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF','PUR_STAFF')")
    @GetMapping("/email-logs")
    public ApiResponse<java.util.List<PublicationStatusResponse.EmailAttempt>> emailLogs(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("PUBLICATION_READ"), service.status(id).emails());
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    @PostMapping("/pdf/regenerate")
    public ApiResponse<PublicationStatusResponse> regenerate(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("PUBLICATION_GENERATED"), service.regenerate(id));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    @PostMapping("/email/resend")
    public ApiResponse<PublicationStatusResponse> resend(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("EMAIL_SIMULATED"), service.resend(id));
    }
}
