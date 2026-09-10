package com.company.itam.workflow.handover.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.workflow.handover.dto.*;
import com.company.itam.workflow.handover.service.HandoverService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/handovers")
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class HandoverController {
    private final HandoverService service;
    private final MessageHelper messages;
    public HandoverController(HandoverService service,MessageHelper messages) { this.service=service; this.messages=messages; }
    @GetMapping("/candidates")
    public ApiResponse<PageResponse<HandoverService.Candidate>> candidates(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.candidates(keyword,page,size));
    }
    @PostMapping("/preview")
    public ApiResponse<HandoverResponse> preview(@Valid @RequestBody HandoverRequest request) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.preview(request));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HandoverResponse> complete(@Valid @RequestBody HandoverRequest request) {
        return ApiResponse.success(messages.getMessage("HANDOVER_COMPLETED"),service.complete(request));
    }
    @GetMapping("/{id}")
    public ApiResponse<HandoverResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.get(id));
    }
}
