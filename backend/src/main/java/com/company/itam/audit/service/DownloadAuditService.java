package com.company.itam.audit.service;

import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DownloadAuditService {
    private final AuditLogRepository logs;
    private final UserRepository users;

    public DownloadAuditService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs; this.users = users;
    }

    @Transactional
    public void record(String type, Long id, String action) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var actor = users.findByPrincipal(authentication == null ? null : authentication.getName()).orElseThrow();
        var entry = new AuditLogEntity();
        entry.setEntityType(type); entry.setEntityId(id); entry.setActor(actor); entry.setAction(action);
        logs.save(entry);
    }
}
