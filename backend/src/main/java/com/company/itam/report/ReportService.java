package com.company.itam.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.LocalDate;
import java.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
@Transactional(readOnly=true)
public class ReportService {
    private final JdbcTemplate jdbc;
    private final ReportAccessService access;
    private final org.springframework.context.MessageSource messages;
    public ReportService(JdbcTemplate jdbc, ReportAccessService access, org.springframework.context.MessageSource messages) { this.jdbc=jdbc; this.access=access; this.messages=messages; }
    public record Filter(String type, String status, Long departmentId, Long locationId, Long userId,
                         Long actorId, String dateField, LocalDate from, LocalDate to, int page, int size) {}
    private record Query(String sql, List<Object> args) {}
    private Query query(ReportAccessService.Group group, Filter f) {
        if (f.page()<0 || f.size()<1 || f.size()>200 || f.from()!=null && f.to()!=null && f.from().isAfter(f.to()))
            throw new com.company.itam.common.exception.AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Invalid report filter");
        if (f.dateField()!=null && !Set.of("created","completed").contains(f.dateField()))
            throw new com.company.itam.common.exception.AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Invalid date field");
        var args=new ArrayList<Object>();
        String sql;
        if(group==ReportAccessService.Group.TRANSACTIONS) {
            sql="SELECT t.transaction_id,t.transaction_code,t.type,t.status,t.created_at,t.completed_at,t.requester_id,creator.full_name AS requester_name,t.processed_by,processor.full_name AS processor_name FROM transactions t LEFT JOIN users creator ON creator.user_id=t.requester_id LEFT JOIN users processor ON processor.user_id=t.processed_by WHERE 1=1";
            sql=eq(sql,args,"t.type",f.type()); sql=eq(sql,args,"t.status",f.status());
            if(f.actorId()!=null) {sql+=" AND (t.processed_by=? OR t.requester_id=?)";args.add(f.actorId());args.add(f.actorId());}
            String date="completed".equals(f.dateField())?"t.completed_at":"t.created_at";
            if("completed".equals(f.dateField())) sql+=" AND t.status='COMPLETED' AND t.completed_at IS NOT NULL";
            if(f.from()!=null) {sql+=" AND "+date+">=?"; args.add(f.from().atStartOfDay());}
            if(f.to()!=null) {sql+=" AND "+date+"<?"; args.add(f.to().plusDays(1).atStartOfDay());}
            sql+=" ORDER BY t.transaction_id";
        } else {
            sql="""
                SELECT a.asset_tag,a.asset_id,a.name,ty.code AS type,c.code AS category,s.code AS status,
                a.department_id,a.location_id,a.assigned_to,u.full_name AS assigned_user,
                (SELECT string_agg(au.full_name || ' (' || al.seat_count || ')', ', ' ORDER BY al.allocation_id)
                 FROM license_allocations al JOIN users au ON au.user_id=al.user_id
                 WHERE al.license_asset_id=a.asset_id AND al.status='ACTIVE' AND (? IS NULL OR al.user_id=?)) AS license_holders,
                (SELECT COALESCE(SUM(al.seat_count),0) FROM license_allocations al
                 WHERE al.license_asset_id=a.asset_id AND al.status='ACTIVE' AND (? IS NULL OR al.user_id=?)) AS employee_seats,
                d.name AS department,l.name AS location,
                ld.seat_count AS total_seats,
                COALESCE(la.active,0) AS allocated_seats,COALESCE(la.reserved,0) AS reserved_seats,
                ld.seat_count-COALESCE(la.active,0)-COALESCE(la.reserved,0) AS available_seats
                FROM assets a JOIN asset_types ty USING(type_id) JOIN asset_statuses s USING(status_id) JOIN asset_categories c ON c.category_id=ty.category_id
                LEFT JOIN users u ON u.user_id=a.assigned_to
                LEFT JOIN departments d ON d.department_id=a.department_id
                LEFT JOIN locations l ON l.location_id=a.location_id
                LEFT JOIN asset_license_details ld ON ld.asset_id=a.asset_id
                LEFT JOIN (SELECT license_asset_id,SUM(CASE WHEN status='ACTIVE' THEN seat_count ELSE 0 END) AS active,
                SUM(CASE WHEN status='RESERVED' THEN seat_count ELSE 0 END) AS reserved
                FROM license_allocations WHERE status<>'RELEASED' GROUP BY license_asset_id) la ON la.license_asset_id=a.asset_id
                WHERE 1=1
                """;
            args.add(f.userId()); args.add(f.userId()); args.add(f.userId()); args.add(f.userId());
            sql=sql.replace("? IS NULL", "CAST(? AS BIGINT) IS NULL");
            sql=eq(sql,args,"ty.code",f.type()); sql=eq(sql,args,"s.code",f.status());
            sql=eq(sql,args,"a.department_id",f.departmentId()); sql=eq(sql,args,"a.location_id",f.locationId());
            if(f.userId()!=null) {
                sql+=" AND (a.assigned_to=? OR EXISTS(SELECT 1 FROM license_allocations ua WHERE ua.license_asset_id=a.asset_id AND ua.user_id=? AND ua.status='ACTIVE'))";
                args.add(f.userId()); args.add(f.userId());
            }
            if(group==ReportAccessService.Group.ALLOCATIONS) sql+=" AND (a.assigned_to IS NOT NULL OR ld.asset_id IS NOT NULL)";
            sql+=" ORDER BY a.asset_id";
        }
        return new Query(sql,args);
    }
    private String eq(String sql,List<Object> args,String column,Object value) {
        if(value==null || value instanceof String s && s.isBlank()) return sql;
        args.add(value); return sql+" AND "+column+"=?";
    }
    public Map<String,Object> options(ReportAccessService.Group group) {
        access.require(group,false);
        var values=new LinkedHashMap<String,Object>();
        values.put("type",jdbc.queryForList(group==ReportAccessService.Group.TRANSACTIONS ? "SELECT DISTINCT type AS id,type AS name FROM transactions ORDER BY type" : "SELECT code AS id,name FROM asset_types ORDER BY name"));
        values.put("status",jdbc.queryForList(group==ReportAccessService.Group.TRANSACTIONS ? "SELECT DISTINCT status AS id,status AS name FROM transactions ORDER BY status" : "SELECT code AS id,name FROM asset_statuses ORDER BY name"));
        if(group==ReportAccessService.Group.TRANSACTIONS) values.put("actorId",jdbc.queryForList("SELECT user_id AS id,full_name AS name FROM users WHERE user_id IN (SELECT requester_id FROM transactions UNION SELECT processed_by FROM transactions) ORDER BY full_name,user_id"));
        else {
            values.put("departmentId",jdbc.queryForList("SELECT department_id AS id,name FROM departments ORDER BY name"));
            values.put("locationId",jdbc.queryForList("SELECT location_id AS id,name FROM locations ORDER BY name"));
            values.put("userId",jdbc.queryForList("SELECT user_id AS id,full_name AS name FROM users WHERE user_id IN (SELECT assigned_to FROM assets UNION SELECT user_id FROM license_allocations WHERE status='ACTIVE') ORDER BY full_name,user_id"));
        }
        return values;
    }
    public Map<String,Object> list(ReportAccessService.Group group,Filter filter) {
        access.require(group,false); var q=query(group,filter);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+q.sql()+") report_rows",Long.class,q.args().toArray());
        var args=new ArrayList<>(q.args()); args.add(filter.size()); args.add((long)filter.page()*filter.size());
        var summary = group==ReportAccessService.Group.TRANSACTIONS
            ? jdbc.queryForList("SELECT type,status,COUNT(*) AS count FROM ("+q.sql()+") r GROUP BY type,status",q.args().toArray())
            : jdbc.queryForList("SELECT category,COUNT(*) AS count,COALESCE(SUM(employee_seats),0) AS employee_seats,COALESCE(SUM(total_seats),0) AS total_seats,COALESCE(SUM(allocated_seats),0) AS allocated_seats,COALESCE(SUM(reserved_seats),0) AS reserved_seats,COALESCE(SUM(available_seats),0) AS available_seats FROM ("+q.sql()+") r GROUP BY category",q.args().toArray());
        return Map.of("summary",summary,"items",jdbc.queryForList(q.sql()+" LIMIT ? OFFSET ?",args.toArray()),"total",total,"page",filter.page(),"size",filter.size());
    }
    public byte[] export(ReportAccessService.Group group,Filter filter) {
        access.require(group,true); var q=query(group,filter);
        var rows=jdbc.queryForList(q.sql(),q.args().toArray());
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet(group.name());
            {
                var keys=jdbc.query(q.sql()+" LIMIT 0",rs -> {
                    var names=new ArrayList<String>();var metadata=rs.getMetaData();
                    for(int i=1;i<=metadata.getColumnCount();i++) names.add(metadata.getColumnLabel(i));return names;
                },q.args().toArray()); var header=sheet.createRow(0);
                for(int i=0;i<keys.size();i++) header.createCell(i).setCellValue(messages.getMessage("report."+keys.get(i),null,keys.get(i),org.springframework.context.i18n.LocaleContextHolder.getLocale()));
                for(int r=0;r<rows.size();r++) {var row=sheet.createRow(r+1);for(int c=0;c<keys.size();c++) {
                    var value=rows.get(r).get(keys.get(c));var cell=row.createCell(c);
                    if(value instanceof Number n) cell.setCellValue(n.doubleValue());else {
                        String text=value==null?"":value.toString();
                        if(Set.of("type","status","category").contains(keys.get(c))) text=messages.getMessage("report.value_"+text,null,text,org.springframework.context.i18n.LocaleContextHolder.getLocale());
                        cell.setCellValue(text);
                    }
                }}
            }
            sheet.createFreezePane(0,1);
            for(int c=0;c<sheet.getRow(0).getLastCellNum();c++) {sheet.autoSizeColumn(c);sheet.setColumnWidth(c,Math.min(60*256,Math.max(12*256,sheet.getColumnWidth(c))));}
            book.write(out); return out.toByteArray();
        } catch(IOException e) {throw new IllegalStateException("Cannot export report",e);}
    }
}

