package com.company.itam.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class ReportAccessService {
    public enum Group { ASSETS, TRANSACTIONS, ALLOCATIONS }
    public record Permission(boolean view, boolean export) {}
    private final JdbcTemplate jdbc;
    private final com.company.itam.user.repository.UserRepository users;
    private final com.company.itam.audit.repository.AuditLogRepository audit;
    public ReportAccessService(JdbcTemplate jdbc, com.company.itam.user.repository.UserRepository users,
            com.company.itam.audit.repository.AuditLogRepository audit) {
        this.jdbc = jdbc; this.users = users; this.audit = audit;
    }
    public void require(Group group, boolean export) {
        var permission = current().get(group);
        if (!permission.view() || (export && !permission.export())) throw new AccessDeniedException("Access denied");
    }
    public Map<Group, Permission> current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> Set.of("ADMIN", "IT_STAFF", "PUR_STAFF").contains(a.getAuthority())))
            throw new AccessDeniedException("Access denied");
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            var result = new EnumMap<Group, Permission>(Group.class);
            for (var group : Group.values()) result.put(group, new Permission(true, true));
            return result;
        }
        var name = auth.getName();
        var ids = name.startsWith("user:")
                ? jdbc.queryForList("SELECT user_id FROM users WHERE user_id=?", Long.class, Long.valueOf(name.substring(5)))
                : jdbc.queryForList("SELECT user_id FROM users WHERE email=?", Long.class, name);
        if (ids.size() != 1) throw new AccessDeniedException("Access denied");
        return permissions(ids.get(0));
    }
    public Map<Group, Permission> permissions(Long userId) {
        var result = new EnumMap<Group, Permission>(Group.class);
        for (var group : Group.values()) result.put(group, new Permission(false, false));
        jdbc.query("SELECT report_group,can_view,can_export FROM report_permissions WHERE user_id=?",
            rs -> { result.put(Group.valueOf(rs.getString(1)), new Permission(rs.getBoolean(2), rs.getBoolean(3))); }, userId);
        return result;
    }
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ADMIN')")
    public void update(Long userId, Group group, Permission permission) {
        if (permission.export() && !permission.view()) throw new com.company.itam.common.exception.AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Export requires view");
        // Use the same lock as identity changes so roles and grants cannot race.
        jdbc.execute("SELECT pg_advisory_xact_lock(7240319)");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        var actor = users.findByPrincipal(auth == null ? null : auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!actor.canLogin() || actor.getRole().getCode() != com.company.itam.common.enums.Role.ADMIN)
            throw new AccessDeniedException("Access denied");
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND r.code IN ('IT_STAFF','PUR_STAFF') AND u.password_hash IS NOT NULL", Long.class, userId);
        if (count == null || count != 1) throw new com.company.itam.common.exception.AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Report permissions require an IT/PUR account");
        var before = permissions(userId).get(group);
        jdbc.update("DELETE FROM report_permissions WHERE user_id=? AND report_group=?", userId, group.name());
        jdbc.update("INSERT INTO report_permissions(user_id,report_group,can_view,can_export) VALUES(?,?,?,?)", userId, group.name(), permission.view(), permission.export());
        var entry = new com.company.itam.audit.entity.AuditLogEntity();
        entry.setActor(actor); entry.setEntityType("USER"); entry.setEntityId(userId);
        entry.setAction("REPORT_PERMISSION_UPDATED");
        entry.setOldData(Map.of("group", group.name(), "view", before.view(), "export", before.export()));
        entry.setNewData(Map.of("group", group.name(), "view", permission.view(), "export", permission.export()));
        audit.save(entry);
    }
}
