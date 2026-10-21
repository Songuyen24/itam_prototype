package com.company.itam.workflow.core.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class WorkflowAccessService {
    private final JdbcTemplate jdbc;
    public WorkflowAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireCreator(TransactionEntity transaction, boolean forceException) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new AccessDeniedException("Access denied");
        if (transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.REJECTED
                || transaction.getStatus() == TransactionStatus.CANCELLED)
            throw new AppException(HttpStatus.CONFLICT,"DOCUMENT_LOCKED","DOCUMENT_LOCKED");
        if (!forceException && (("user:" + transaction.getRequester().getUserId()).equals(auth.getName()) || java.util.Objects.equals(transaction.getRequester().getEmail(), auth.getName()))) return;
        if (auth.getAuthorities().stream().noneMatch(a -> "ADMIN".equals(a.getAuthority())))
            throw new AccessDeniedException("Access denied");
        var attributes = RequestContextHolder.getRequestAttributes();
        String reason = attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest().getHeader("X-Override-Reason") : null;
        if (reason != null) {
            try { reason = java.net.URLDecoder.decode(reason, java.nio.charset.StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalid) {
                throw new AppException(HttpStatus.BAD_REQUEST,"OVERRIDE_REASON_REQUIRED","OVERRIDE_REASON_REQUIRED");
            }
        }
        if (reason == null || reason.isBlank() || reason.length() > 4000)
            throw new AppException(HttpStatus.BAD_REQUEST,"OVERRIDE_REASON_REQUIRED","OVERRIDE_REASON_REQUIRED");
        jdbc.update("""
                INSERT INTO audit_logs(entity_type,entity_id,transaction_id,actor_user_id,action,details)
                SELECT 'TRANSACTION',?,?,user_id,'ADMIN_OVERRIDE',? FROM users WHERE ('user:' || user_id)=? OR email=?
                """,transaction.getTransactionId(),transaction.getTransactionId(),reason.trim(),auth.getName(),auth.getName());
    }
}
