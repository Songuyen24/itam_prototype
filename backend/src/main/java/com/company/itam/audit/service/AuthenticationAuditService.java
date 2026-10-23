package com.company.itam.audit.service;

import com.company.itam.audit.entity.AuditLogEntity;
import com.company.itam.audit.repository.AuditLogRepository;
import com.company.itam.user.entity.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationAuditService {
    private final AuditLogRepository logs;

    public AuthenticationAuditService(AuditLogRepository logs) { this.logs = logs; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UserEntity user, String action) {
        var entry = new AuditLogEntity();
        entry.setEntityType("USER");
        entry.setEntityId(user == null ? 0L : user.getUserId());
        entry.setActor(user);
        entry.setAction(action);
        logs.save(entry);
    }
}
