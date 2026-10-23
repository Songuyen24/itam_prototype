package com.company.itam.audit.controller;

import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.common.exception.AppException;
import com.company.itam.common.response.ApiResponse;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/audit-logs")
@PreAuthorize("hasAuthority('ADMIN')")
public class AuditLogController {
    private final AuditLogRepository logs;

    public AuditLogController(AuditLogRepository logs) { this.logs = logs; }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100 || from != null && to != null && !from.isBefore(to))
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid audit filter");
        var result = logs.search(assetId, actorId, action == null || action.isBlank() ? null : action.trim(),
                from == null ? Instant.parse("1900-01-01T00:00:00Z") : from,
                to == null ? Instant.parse("9999-01-01T00:00:00Z") : to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "auditLogId")));
        var rows = result.map(log -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", log.getAuditLogId()); row.put("entityType", log.getEntityType());
            row.put("entityId", log.getEntityId());
            row.put("actorId", log.getActor() == null ? null : log.getActor().getUserId());
            row.put("actor", log.getActor() == null ? null : log.getActor().getFullName());
            row.put("action", log.getAction()); row.put("createdAt", log.getCreatedAt());
            row.put("transactionId", log.getTransaction() == null ? null : log.getTransaction().getTransactionId());
            row.put("oldData", redact(log.getOldData())); row.put("newData", redact(log.getNewData()));
            row.put("details", log.getDetails() != null && log.getDetails().matches("(?is).*(password|token|secret|license.?key|hash).*")
                    ? "[redacted]" : log.getDetails());
            row.put("result", log.getAction().endsWith("FAILED") || log.getAction().endsWith("DENIED")
                    || log.getAction().endsWith("BLOCKED") ? "FAILURE" : "SUCCESS");
            return row;
        }).getContent();
        return ApiResponse.success("OK", Map.of("content", rows, "page", result.getNumber(),
                "size", result.getSize(), "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()));
    }

    private static Object redact(Object value) {
        if (value instanceof Map<?, ?> values) {
            var safe = new LinkedHashMap<String, Object>();
            values.forEach((key, item) -> {
                String name = String.valueOf(key);
                String lower = name.toLowerCase(Locale.ROOT);
                safe.put(name, lower.contains("password") || lower.contains("token") || lower.contains("secret")
                        || lower.contains("licensekey") || lower.contains("license_key") || lower.contains("hash")
                        ? "[redacted]" : redact(item));
            });
            return safe;
        }
        if (value instanceof List<?> items) return items.stream().map(AuditLogController::redact).toList();
        return value;
    }
}
