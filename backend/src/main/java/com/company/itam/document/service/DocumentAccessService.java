package com.company.itam.document.service;

import com.company.itam.common.enums.DocumentType;
import com.company.itam.common.exception.AppException;
import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.user.repository.UserRepository;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionType;
import com.company.itam.workflow.handover.repository.TransactionHandoverDetailRepository;
import com.company.itam.workflow.recovery.repository.TransactionRecoveryDetailRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DocumentAccessService {

    private final UserRepository userRepository;
    private final TransactionHandoverDetailRepository handoverDetailRepository;
    private final TransactionRecoveryDetailRepository recoveryDetailRepository;

    public DocumentAccessService(UserRepository userRepository,
                                 TransactionHandoverDetailRepository handoverDetailRepository,
                                 TransactionRecoveryDetailRepository recoveryDetailRepository) {
        this.userRepository = userRepository;
        this.handoverDetailRepository = handoverDetailRepository;
        this.recoveryDetailRepository = recoveryDetailRepository;
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
        if (!hasAuthority(authentication, "USER")) {
            throw denied();
        }
        Long userId = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"))
                .getUserId();
        boolean ownReport = false;
        if (transaction.getType() == TransactionType.HANDOVER
                && document.getDocumentType() == DocumentType.HANDOVER_REPORT) {
            ownReport = handoverDetailRepository.findById(transaction.getTransactionId())
                    .map(detail -> Objects.equals(detail.getRecipient().getUserId(), userId)).orElse(false);
        } else if (transaction.getType() == TransactionType.RECOVERY
                && document.getDocumentType() == DocumentType.RECOVERY_REPORT) {
            ownReport = recoveryDetailRepository.findById(transaction.getTransactionId())
                    .map(detail -> Objects.equals(detail.getReturner().getUserId(), userId)).orElse(false);
        }
        if (!ownReport) {
            throw denied();
        }
    }

    public void requireImportEditor(TransactionEntity transaction) {
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
