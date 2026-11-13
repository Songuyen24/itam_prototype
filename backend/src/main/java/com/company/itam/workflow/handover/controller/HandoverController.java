package com.company.itam.workflow.handover.controller;

import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.workflow.handover.dto.*;
import com.company.itam.workflow.handover.service.HandoverService;
import com.company.itam.workflow.core.dto.WorkflowProgressModels;
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
    private final com.company.itam.workflow.handover.service.HandoverHolderService holders;
    public HandoverController(HandoverService service,MessageHelper messages,
            com.company.itam.workflow.handover.service.HandoverHolderService holders) {
        this.service=service; this.messages=messages; this.holders=holders;
    }
    @GetMapping("/holders")
    public ApiResponse<PageResponse<com.company.itam.workflow.handover.service.HandoverHolderService.Holder>> holders(
            @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="true") boolean heldOnly,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),holders.list(keyword,heldOnly,page,size));
    }
    @GetMapping("/holders/{id}")
    public ApiResponse<com.company.itam.workflow.handover.service.HandoverHolderService.Detail> holder(@PathVariable Long id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),holders.detail(id,page,size));
    }
    @GetMapping("/candidates")
    public ApiResponse<PageResponse<HandoverService.Candidate>> candidates(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.candidates(keyword,page,size));
    }
    @PostMapping("/preview")
    public ApiResponse<HandoverResponse> preview(@Valid @RequestBody HandoverRequest request) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.preview(request));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HandoverResponse> create(@Valid @RequestBody HandoverRequest request) {
        var result=service.create(request);
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),result);
    }
    @PutMapping("/{id}")
    public ApiResponse<HandoverResponse> update(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version,@Valid @RequestBody HandoverRequest request) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.update(id,request,version));
    }
    @PostMapping("/{id}/submit")
    public ApiResponse<HandoverResponse> submit(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.submit(id,version));
    }
    @PostMapping("/{id}/withdraw")
    public ApiResponse<HandoverResponse> withdraw(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.change(id,"withdraw",version));
    }
    @PostMapping("/{id}/cancel")
    public ApiResponse<HandoverResponse> cancel(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.change(id,"cancel",version));
    }
    @PostMapping("/{id}/confirm")
    public ApiResponse<HandoverResponse> confirm(@PathVariable Long id,@RequestHeader("X-Expected-Version") Long version) {
        return ApiResponse.success(messages.getMessage("HANDOVER_COMPLETED"),service.confirm(id,version));
    }
    @PostMapping(value="/{id}/confirm",consumes="application/json")
    public ApiResponse<HandoverResponse> confirmSelected(@PathVariable Long id,@Valid @RequestBody WorkflowProgressModels.Action action) {
        return ApiResponse.success(messages.getMessage("HANDOVER_COMPLETED"),service.confirm(id,action));
    }
    @PostMapping("/{id}/cancel-lines")
    public ApiResponse<HandoverResponse> cancelLines(@PathVariable Long id,@Valid @RequestBody WorkflowProgressModels.Action action) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.cancelLines(id,action));
    }
    @GetMapping("/{id}/progress")
    public ApiResponse<java.util.Map<String,Object>> progress(@PathVariable Long id) {
        var detail=service.get(id);
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),java.util.Map.of("contentVersion",detail.contentVersion(),"groups",detail.groups()));
    }
    @GetMapping("/{id}")
    public ApiResponse<HandoverResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(messages.getMessage("HANDOVER_READ"),service.get(id));
    }
}
