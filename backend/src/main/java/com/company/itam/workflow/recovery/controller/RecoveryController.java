package com.company.itam.workflow.recovery.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.workflow.recovery.dto.*;
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
        var result = service.complete(request);
        publications.afterCommit(result.transactionId(), () -> publications.onCompleted(result.transactionId(), result));
        return ApiResponse.success(messages.getMessage("RECOVERY_COMPLETED"), result);
    }
}
