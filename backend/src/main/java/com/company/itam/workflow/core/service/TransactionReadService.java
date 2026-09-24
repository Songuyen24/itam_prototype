package com.company.itam.workflow.core.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.common.pagination.PageResponse;
import com.company.itam.document.service.DocumentAccessService;
import com.company.itam.workflow.core.dto.TransactionSummaryResponse;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import com.company.itam.workflow.core.enums.TransactionType;
import com.company.itam.workflow.core.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class TransactionReadService {

    private final TransactionRepository transactionRepository;
    private final DocumentAccessService accessService;

    public TransactionReadService(TransactionRepository transactionRepository, DocumentAccessService accessService) {
        this.transactionRepository = transactionRepository;
        this.accessService = accessService;
    }

    public PageResponse<TransactionSummaryResponse> getTransactions(TransactionType type, TransactionStatus status,
                                                                     String keyword, int page, int size) {
        accessService.requireTransactionReader();
        if (page < 0 || size < 1 || size > 100 || (keyword != null && keyword.length() > 100)) {
            throw invalidRequest();
        }
        TransactionType permittedType = type;
        if (accessService.isPurchasingReader()) {
            if (type != null && type != TransactionType.IMPORT) {
                throw new AccessDeniedException("Access denied");
            }
            permittedType = TransactionType.IMPORT;
        }
        TransactionType filterType = permittedType;
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return PageResponse.of(transactionRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filterType != null) {
                predicates.add(builder.equal(root.get("type"), filterType));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (!search.isEmpty()) {
                predicates.add(builder.like(builder.lower(root.get("transactionCode")), "%" + search + "%", '\\'));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "transactionId")))
                .map(TransactionSummaryResponse::fromEntity));
    }

    public TransactionSummaryResponse getTransaction(Long id) {
        accessService.requireTransactionReader();
        if (id == null || id <= 0) {
            throw invalidRequest();
        }
        TransactionEntity transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found"));
        accessService.requireRead(transaction);
        return TransactionSummaryResponse.fromEntity(transaction);
    }

    private AppException invalidRequest() {
        return new AppException(HttpStatus.BAD_REQUEST, "TRANSACTION_REQUEST_INVALID", "Invalid transaction request");
    }
}
