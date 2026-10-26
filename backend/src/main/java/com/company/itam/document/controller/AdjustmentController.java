package com.company.itam.document.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.document.service.AdjustmentService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/v1/transactions/{transactionId}/adjustments")
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF','PUR_STAFF')")
public class AdjustmentController {
    private final AdjustmentService service;
    private final com.company.itam.audit.service.DownloadAuditService downloads;
    public AdjustmentController(AdjustmentService service,com.company.itam.audit.service.DownloadAuditService downloads) { this.service=service; this.downloads=downloads; }
    @GetMapping
    public ApiResponse<AdjustmentService.Overview> list(@PathVariable long transactionId,@RequestParam(defaultValue="0") int page) {
        return ApiResponse.success("OK",service.list(transactionId,page));
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdjustmentService.Adjustment> create(@PathVariable long transactionId,
            @RequestParam String kind,@RequestParam String reason,@RequestParam String proposedText,
            @RequestParam(required=false) Long correctiveTransactionId,@RequestParam(required=false) List<MultipartFile> files) {
        return ApiResponse.success("OK",service.create(transactionId,kind,reason,proposedText,correctiveTransactionId,files));
    }
    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<AdjustmentService.Adjustment> decide(@PathVariable long transactionId,@PathVariable long id,
            @RequestBody AdjustmentService.Decision decision) {
        return ApiResponse.success("OK",service.decide(transactionId,id,decision));
    }
    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable long transactionId,@PathVariable long id,@PathVariable long fileId) {
        var data=service.download(transactionId,id,fileId);
        downloads.record("TRANSACTION",transactionId,"ADJUSTMENT_FILE_DOWNLOADED");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(data.file().mimeType()))
            .contentLength(data.content().length).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(data.file().name().replaceAll("[\\p{Cntrl}/\\\\]","_"),StandardCharsets.UTF_8).build().toString())
            .body(new ByteArrayResource(data.content()));
    }
}



