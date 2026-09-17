package com.company.itam.workflow.disposal.controller;

import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.publication.service.TransactionPublicationService;
import com.company.itam.workflow.disposal.dto.DisposalModels.*;
import com.company.itam.workflow.disposal.service.DisposalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/disposals")
public class DisposalController {
    private final DisposalService service;
    private final MessageHelper messages;
    private final TransactionPublicationService publications;

    public DisposalController(DisposalService service, MessageHelper messages, TransactionPublicationService publications) {
        this.service = service; this.messages = messages; this.publications = publications;
    }

    @GetMapping("/candidates")
    public ApiResponse<PageResponse<AssetLine>> candidates(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(messages.getMessage("DISPOSAL_READ"), service.candidates(keyword, page, size));
    }

    @PostMapping("/smart-check")
    public ApiResponse<SmartCheckResponse> smartCheck(@Valid @RequestBody SmartCheckRequest request) {
        return ApiResponse.success(messages.getMessage("DISPOSAL_READ"), service.smartCheck(request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DisposalResponse>> create(@Valid @RequestBody CreateRequest request) {
        var result = service.create(request);
        publications.afterCommit(result.transactionId(), () -> publications.onDisposalPending(result.transactionId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(messages.getMessage("DISPOSAL_CREATED"), result));
    }

    @GetMapping("/{id}")
    public ApiResponse<DisposalResponse> get(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("DISPOSAL_READ"), service.get(id));
    }

    @PostMapping("/{id}/per-user")
    public ApiResponse<DisposalResponse> resolvePerUser(@PathVariable Long id,
            @Valid @RequestBody PerUserRequest request) {
        return ApiResponse.success(messages.getMessage("DISPOSAL_PER_USER_RESOLVED"), service.resolvePerUser(id, request));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<DisposalResponse> approve(@PathVariable Long id,
            @Valid @RequestBody ApproveRequest request) {
        var result = service.approve(id, request);
        publications.afterCommit(id, () -> publications.onCompleted(id, result));
        return ApiResponse.success(messages.getMessage("DISPOSAL_APPROVED"), result);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<DisposalResponse> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return ApiResponse.success(messages.getMessage("DISPOSAL_REJECTED"), service.reject(id, request));
    }
}
