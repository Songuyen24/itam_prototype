package com.company.itam.workflow.recovery.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.workflow.recovery.dto.*;
import com.company.itam.workflow.core.dto.WorkflowProgressModels;
import com.company.itam.workflow.recovery.service.RecoveryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/recoveries")
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class RecoveryController {
    private final RecoveryService service;
    private final MessageHelper messages;
    private final com.company.itam.publication.service.TransactionPublicationService publications;

    public RecoveryController(RecoveryService service, MessageHelper messages,
                              com.company.itam.publication.service.TransactionPublicationService publications) {
        this.service = service;
        this.messages = messages;
        this.publications = publications;
    }

    @PostMapping("/smart-check")
    public ApiResponse<SmartCheckResponse> smartCheck(@Valid @RequestBody SmartCheckRequest request) {
        return ApiResponse.success(messages.getMessage("RECOVERY_READ"), service.smartCheck(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecoveryResponse> complete(@Valid @RequestBody RecoveryRequest request) {
        return ApiResponse.success(messages.getMessage("RECOVERY_CREATED"), service.create(request));
    }
    @GetMapping
    public ApiResponse<com.company.itam.common.pagination.PageResponse<RecoveryResponse>> list(
            @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(messages.getMessage("RECOVERY_READ"),service.list(keyword,status,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<RecoveryResponse> get(@PathVariable Long id) {return ApiResponse.success(messages.getMessage("RECOVERY_READ"),service.get(id));}
    @PutMapping("/{id}")
    public ApiResponse<RecoveryResponse> update(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version,@Valid @RequestBody RecoveryRequest request) {return ApiResponse.success(messages.getMessage("RECOVERY_UPDATED"),service.update(id,request,version));}
    @PostMapping("/{id}/submit")
    public ApiResponse<RecoveryResponse> submit(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version) {return ApiResponse.success(messages.getMessage("RECOVERY_SUBMITTED"),service.submit(id,version));}
    @PostMapping("/{id}/withdraw")
    public ApiResponse<RecoveryResponse> withdraw(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version) {return ApiResponse.success(messages.getMessage("RECOVERY_WITHDRAWN"),service.change(id,"withdraw",version));}
    @PostMapping("/{id}/cancel")
    public ApiResponse<RecoveryResponse> cancel(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version) {return ApiResponse.success(messages.getMessage("RECOVERY_CANCELLED"),service.change(id,"cancel",version));}
    @PostMapping("/{id}/confirm")
    public ApiResponse<RecoveryResponse> confirm(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version) {return ApiResponse.success(messages.getMessage("RECOVERY_COMPLETED"),service.confirm(id,version));}
    @PostMapping(value="/{id}/confirm",consumes="application/json")
    public ApiResponse<RecoveryResponse> confirmSelected(@PathVariable Long id,@Valid @RequestBody WorkflowProgressModels.Action action) {return ApiResponse.success(messages.getMessage("RECOVERY_COMPLETED"),service.confirm(id,action));}
    @PostMapping("/{id}/cancel-remaining")
    public ApiResponse<RecoveryResponse> cancelRemaining(@PathVariable Long id,@Valid @RequestBody WorkflowProgressModels.Action action) {return ApiResponse.success(messages.getMessage("RECOVERY_CANCELLED"),service.cancelRemaining(id,action));}
}
