package com.company.itam.workflow.core.controller;

import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.response.ApiResponse;
import com.company.itam.common.util.MessageHelper;
import com.company.itam.workflow.core.dto.TransactionSummaryResponse;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.enums.TransactionType;
import com.company.itam.workflow.core.service.TransactionReadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/transactions")
@PreAuthorize("hasAnyAuthority('ADMIN', 'IT_STAFF', 'PUR_STAFF')")
public class TransactionReadController {

    private final TransactionReadService transactionService;
    private final MessageHelper messageHelper;

    public TransactionReadController(TransactionReadService transactionService, MessageHelper messageHelper) {
        this.transactionService = transactionService;
        this.messageHelper = messageHelper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TransactionSummaryResponse>>> getTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(messageHelper.getMessage("TRANSACTION_LIST_SUCCESS"),
                transactionService.getTransactions(type, status, keyword, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionSummaryResponse>> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(messageHelper.getMessage("TRANSACTION_GET_SUCCESS"),
                transactionService.getTransaction(id)));
    }
}
