package com.company.itam.dashboard;

import com.company.itam.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final JdbcTemplate jdbc;
    public DashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Aggregate each one-to-many source before joining, so a multi-line voucher is counted once.
    private static final String EVENTS = """
        SELECT t.*, COALESCE(i.devices,0) AS devices, COALESCE(i.components,0) AS components,
            COALESCE(i.packages,0) AS packages,
            CASE WHEN t.type='DISPOSAL' THEN NULL WHEN COALESCE(i.packages,0)=0 THEN 0
                 WHEN t.type IN ('HANDOVER','RECOVERY') AND COALESCE(hs.snapshot,ps.snapshot)->'lines' IS NOT NULL
                 THEN (SELECT SUM(COALESCE((line->>'seats')::bigint,0))
                       FROM jsonb_array_elements(COALESCE(hs.snapshot,ps.snapshot)->'lines') line)
                 WHEN t.type='IMPORT' AND COALESCE(ps.snapshot,rev.snapshot)->'assets' IS NOT NULL
                 THEN (SELECT SUM(COALESCE((line->'license'->>'seat_count')::bigint,(line->'license'->>'seatCount')::bigint,0))
                       FROM jsonb_array_elements(COALESCE(ps.snapshot,rev.snapshot)->'assets') line)
                 ELSE NULL END AS seats,
            hd.recipient_user_id, COALESCE(recipient.full_name,returner.full_name,'') AS recipient,
            COALESCE(processor.full_name,creator.full_name,'') AS actor
        FROM transactions t
        LEFT JOIN (SELECT ta.transaction_id,
            COUNT(*) FILTER (WHERE c.code='DEVICE') AS devices,
            COUNT(*) FILTER (WHERE c.code='COMPONENT') AS components,
            COUNT(*) FILTER (WHERE c.code='LICENSE') AS packages
            FROM transaction_assets ta JOIN assets a USING(asset_id)
            JOIN asset_types ty USING(type_id) JOIN asset_categories c USING(category_id)
            GROUP BY ta.transaction_id) i USING(transaction_id)
        LEFT JOIN transaction_handover_details hd USING(transaction_id)
        LEFT JOIN transaction_recovery_details rd USING(transaction_id)
        LEFT JOIN users recipient ON recipient.user_id=hd.recipient_user_id
        LEFT JOIN users returner ON returner.user_id=rd.returner_user_id
        LEFT JOIN users creator ON creator.user_id=t.requester_id
        LEFT JOIN users processor ON processor.user_id=t.processed_by
        LEFT JOIN handover_snapshots hs USING(transaction_id)
        LEFT JOIN transaction_publication_snapshots ps USING(transaction_id)
        LEFT JOIN transaction_revisions rev ON rev.transaction_id=t.transaction_id AND rev.revision=t.submitted_revision
        """;
    private static final String COMPLETED = " WHERE status='COMPLETED' AND completed_at>=? AND completed_at<?";

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public Map<String, Object> summary() { return summary("thisMonth",null,null); }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public Map<String, Object> summary(String period, LocalDate from, LocalDate to) {
        LocalDate month = jdbc.queryForObject("SELECT CURRENT_DATE",LocalDate.class).withDayOfMonth(1);
        if ("thisMonth".equals(period)) { from=month; to=month.plusMonths(1).minusDays(1); }
        else if ("lastMonth".equals(period)) { from=month.minusMonths(1); to=month.minusDays(1); }
        else if (!"custom".equals(period)) throw invalidPeriod();
        if (from==null || to==null || from.isAfter(to) || to.equals(LocalDate.MAX)) throw invalidPeriod();
        Object[] bounds = {from.atStartOfDay(),to.plusDays(1).atStartOfDay()};
        var result = new LinkedHashMap<String, Object>();
        result.put("from",from); result.put("to",to);
        result.put("byStatus", counts("SELECT s.code,COUNT(*) FROM assets a JOIN asset_statuses s USING(status_id) JOIN asset_types ty USING(type_id) JOIN asset_categories c USING(category_id) WHERE c.code='DEVICE' GROUP BY s.code ORDER BY s.code"));
        result.put("byType", counts("SELECT t.code,COUNT(*) FROM assets a JOIN asset_types t USING(type_id) GROUP BY t.code ORDER BY t.code"));
        result.put("byLocation", counts("SELECT COALESCE(l.name,''),COUNT(*) FROM assets a LEFT JOIN locations l USING(location_id) GROUP BY COALESCE(l.name,'') ORDER BY 1"));
        result.put("byDepartment", counts("SELECT COALESCE(d.name,''),COUNT(*) FROM assets a LEFT JOIN departments d USING(department_id) GROUP BY COALESCE(d.name,'') ORDER BY 1"));
        result.put("damagedDevices",jdbc.queryForObject("SELECT COUNT(*) FROM assets a JOIN asset_types ty USING(type_id) JOIN asset_categories c USING(category_id) JOIN asset_hardware_details h USING(asset_id) JOIN asset_conditions ac USING(condition_id) WHERE c.code='DEVICE' AND ac.code='DAMAGED'",Long.class));
        result.put("inventory",jdbc.queryForList("""
            SELECT c.code AS category,COUNT(*) AS count,COALESCE(SUM(ld.seat_count),0) AS total_seats,
                COALESCE(SUM(la.active),0) AS active_seats,COALESCE(SUM(la.reserved),0) AS reserved_seats,
                COALESCE(SUM(ld.seat_count-COALESCE(la.active,0)-COALESCE(la.reserved,0)),0) AS available_seats
            FROM assets a JOIN asset_types ty USING(type_id) JOIN asset_categories c USING(category_id)
            LEFT JOIN asset_license_details ld USING(asset_id)
            LEFT JOIN (SELECT license_asset_id,SUM(CASE WHEN status='ACTIVE' THEN seat_count ELSE 0 END) AS active,
                SUM(CASE WHEN status='RESERVED' THEN seat_count ELSE 0 END) AS reserved
                FROM license_allocations WHERE status<>'RELEASED' GROUP BY license_asset_id) la ON la.license_asset_id=a.asset_id
            GROUP BY c.code ORDER BY c.code
            """));
        result.put("pendingReceivings", pending("IMPORT")); result.put("pendingDisposals", pending("DISPOSAL"));
        result.put("pending",counts("SELECT type,COUNT(*) FROM transactions WHERE status='PENDING' GROUP BY type ORDER BY type"));
        result.put("drafts",counts("SELECT type,COUNT(*) FROM transactions WHERE status='DRAFT' GROUP BY type ORDER BY type"));
        result.put("failedEmails",jdbc.queryForObject("""
            SELECT COUNT(*) FROM email_logs e WHERE e.status='FAILED' AND NOT EXISTS
            (SELECT 1 FROM email_logs newer WHERE newer.transaction_id=e.transaction_id AND newer.recipient=e.recipient
             AND newer.event_type IS NOT DISTINCT FROM e.event_type
             AND (newer.created_at,newer.email_log_id)>(e.created_at,e.email_log_id))
            """,Long.class));
        result.put("failedPdfs",jdbc.queryForObject("""
            SELECT COUNT(DISTINCT p.transaction_id) FROM publication_attempts p WHERE NOT EXISTS
            (SELECT 1 FROM documents d WHERE d.transaction_id=p.transaction_id AND d.issued_at>=p.created_at)
            """,Long.class));
        result.put("movements",jdbc.queryForList("SELECT type,COUNT(*) AS transactions,SUM(devices) AS devices,SUM(components) AS components,SUM(packages) AS packages,CASE WHEN COUNT(*) FILTER (WHERE seats IS NULL)>0 THEN NULL ELSE SUM(seats) END AS seats,COUNT(DISTINCT recipient_user_id) AS recipients FROM ("+EVENTS+") e"+COMPLETED+" GROUP BY type ORDER BY type",bounds));
        result.put("daily",jdbc.queryForList("SELECT CAST(completed_at AS date) AS day,type,COUNT(*) AS transactions FROM transactions"+COMPLETED+" GROUP BY CAST(completed_at AS date),type ORDER BY day,type",bounds));
        result.put("recent",jdbc.queryForList("SELECT transaction_id,transaction_code,type,status,created_at,completed_at,actor,recipient,devices,components,packages,seats FROM ("+EVENTS+") e"+COMPLETED+" ORDER BY completed_at DESC,transaction_id DESC LIMIT 10",bounds));
        return result;
    }
    private AppException invalidPeriod() { return new AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Invalid dashboard period"); }
    private Map<String, Long> counts(String sql) {
        var result = new LinkedHashMap<String, Long>(); jdbc.query(sql, rs -> { result.put(rs.getString(1), rs.getLong(2)); }); return result;
    }
    private long pending(String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type=? AND status='PENDING'", Long.class, type); }
}
