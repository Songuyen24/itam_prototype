package com.company.itam.document.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


@Service
public class DocumentAccessService {

    private final com.company.itam.workflow.core.service.WorkflowAccessService workflowAccess;
    public DocumentAccessService(com.company.itam.workflow.core.service.WorkflowAccessService workflowAccess) {
        this.workflowAccess = workflowAccess;
    }

    public void requireTransactionReader() {
        Authentication authentication = requireAuthentication();
        if (!isManager(authentication) && !hasAuthority(authentication, "PUR_STAFF")) {
            throw denied();
        }
    }

    public boolean isPurchasingReader() {
        Authentication authentication = requireAuthentication();
        return !isManager(authentication) && hasAuthority(authentication, "PUR_STAFF");
    }

    public void requireRead(TransactionEntity transaction) {
        Authentication authentication = requireAuthentication();
        if (isManager(authentication)
                || (hasAuthority(authentication, "PUR_STAFF") && transaction.getType() == TransactionType.IMPORT)) {
            return;
        }
        throw denied();
    }

    public void requireDownload(DocumentEntity document) {
        Authentication authentication = requireAuthentication();
        TransactionEntity transaction = document.getTransaction();
        if (isManager(authentication)
                || (hasAuthority(authentication, "PUR_STAFF") && transaction.getType() == TransactionType.IMPORT)) {
            return;
        }
        throw denied();
    }

    public void requireImportEditor(TransactionEntity transaction) {
        requireImportEditor(transaction, false);
    }

    public void requireImportEditor(TransactionEntity transaction, boolean forceException) {
        requireImportRole(transaction);
        if (transaction.getTransactionId() != null) workflowAccess.requireCreator(transaction, forceException);
    }

    public void requireImportRole(TransactionEntity transaction) {
        Authentication authentication = requireAuthentication();
        if (transaction.getType() != TransactionType.IMPORT
                || (!hasAuthority(authentication, "ADMIN") && !hasAuthority(authentication, "PUR_STAFF"))) {
            throw denied();
        }
    }

    private Authentication requireAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        }
        return authentication;
    }

    private boolean isManager(Authentication authentication) {
        return hasAuthority(authentication, "ADMIN") || hasAuthority(authentication, "IT_STAFF");
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }
}
