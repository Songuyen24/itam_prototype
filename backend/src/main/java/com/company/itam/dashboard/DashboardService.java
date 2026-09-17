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

    @PreAuthorize("hasAuthority('ADMIN')")
    public byte[] exportAssets() {
        String sql = """
                SELECT a.asset_tag,a.name,t.name,c.name,h.serial_number,s.code,d.name,l.name,u.full_name,
                       ld.seat_count,COALESCE((SELECT SUM(la.seat_count) FROM license_allocations la WHERE la.license_asset_id=a.asset_id AND la.status<>'RELEASED'),0),
                       a.purchase_cost,a.purchase_date
                FROM assets a JOIN asset_types t USING(type_id) JOIN asset_categories c USING(category_id)
                JOIN asset_statuses s USING(status_id) LEFT JOIN asset_hardware_details h USING(asset_id)
                LEFT JOIN departments d USING(department_id) LEFT JOIN locations l USING(location_id)
                LEFT JOIN users u ON u.user_id=a.assigned_to LEFT JOIN asset_license_details ld USING(asset_id)
                ORDER BY a.asset_tag
                """;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Assets");
            String[] headers = {"Asset Tag", "Name", "Type", "Category", "Serial Number", "Status", "Department", "Location", "Assigned User", "Total Seats", "Available Seats", "Allocated Seats", "Purchase Cost", "Purchase Date"};
            var header = sheet.createRow(0); for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            final int[] row = {1};
            jdbc.query(sql, rs -> {
                var current = sheet.createRow(row[0]++);
                for (int i = 1; i <= 9; i++) current.createCell(i - 1).setCellValue(value(rs.getString(i)));
                int total = rs.getInt(10), allocated = rs.getInt(11);
                current.createCell(9).setCellValue(total); current.createCell(10).setCellValue(Math.max(0, total - allocated)); current.createCell(11).setCellValue(allocated);
                if (rs.getBigDecimal(12) != null) current.createCell(12).setCellValue(rs.getBigDecimal(12).doubleValue());
                Date date = rs.getDate(13); current.createCell(13).setCellValue(date == null ? "" : date.toLocalDate().toString());
            });
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(output); return output.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("Cannot export asset report", ex); }
    }

    private Map<String, Long> counts(String sql) {
        var result = new LinkedHashMap<String, Long>(); jdbc.query(sql, rs -> { result.put(rs.getString(1), rs.getLong(2)); }); return result;
    }
    private long pending(String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type=? AND status='PENDING'", Long.class, type); }
    private String value(String value) { return value == null ? "" : value; }
}
