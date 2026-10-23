package com.company.itam.dashboard;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final JdbcTemplate jdbc;
    public DashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
    public Map<String, Object> summary() {
        var result = new LinkedHashMap<String, Object>();
        result.put("byStatus", counts("SELECT s.code,COUNT(*) FROM assets a JOIN asset_statuses s USING(status_id) GROUP BY s.code ORDER BY s.code"));
        result.put("byType", counts("SELECT t.code,COUNT(*) FROM assets a JOIN asset_types t USING(type_id) GROUP BY t.code ORDER BY t.code"));
        result.put("byLocation", counts("SELECT COALESCE(l.name,''),COUNT(*) FROM assets a LEFT JOIN locations l USING(location_id) GROUP BY COALESCE(l.name,'') ORDER BY 1"));
        result.put("byDepartment", counts("SELECT COALESCE(d.name,''),COUNT(*) FROM assets a LEFT JOIN departments d USING(department_id) GROUP BY COALESCE(d.name,'') ORDER BY 1"));
        result.put("pendingReceivings", pending("IMPORT"));
        result.put("pendingDisposals", pending("DISPOSAL"));
        return result;
    }

    private Map<String, Long> counts(String sql) {
        var result = new LinkedHashMap<String, Long>(); jdbc.query(sql, rs -> { result.put(rs.getString(1), rs.getLong(2)); }); return result;
    }
    private long pending(String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type=? AND status='PENDING'", Long.class, type); }
    private String value(String value) { return value == null ? "" : value; }
}
