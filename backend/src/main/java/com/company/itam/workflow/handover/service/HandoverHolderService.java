package com.company.itam.workflow.handover.service;

import com.company.itam.common.pagination.PageResponse;
import com.company.itam.common.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
@Transactional(readOnly=true)
@PreAuthorize("hasAnyAuthority('ADMIN','IT_STAFF')")
public class HandoverHolderService {
    private final JdbcTemplate jdbc;
    public HandoverHolderService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Holder(Long id,String fullName,String email,String employeeCode,String departmentName,String accountStatus,
                         long deviceCount,long componentCount,long licenseSeats) {}
    public record HeldAsset(Long assetId,String assetTag,String name,String category,Long allocationId,int seats,String status,String serialNumber,String deviceTag,String deviceName) {}
    public record Detail(Holder holder,PageResponse<HeldAsset> assets) {}
    private static final String HELD="""
        SELECT a.assigned_to AS user_id,a.asset_id,a.asset_tag,a.name,c.code AS category,
               NULL::bigint AS allocation_id,1 AS seats,s.code AS status,hd.serial_number,NULL::varchar AS device_tag,NULL::varchar AS device_name
        FROM assets a JOIN asset_types t USING(type_id) JOIN asset_categories c USING(category_id)
        JOIN asset_statuses s USING(status_id) LEFT JOIN asset_hardware_details hd ON hd.asset_id=a.asset_id
        WHERE a.assigned_to IS NOT NULL AND c.code IN ('DEVICE','COMPONENT')
        UNION ALL
        SELECT l.user_id,a.asset_id,a.asset_tag,a.name,'LICENSE',l.allocation_id,l.seat_count,l.status,NULL::varchar,device.asset_tag,device.name
        FROM license_allocations l JOIN assets a ON a.asset_id=l.license_asset_id
        LEFT JOIN assets device ON device.asset_id=l.device_asset_id
        WHERE l.status='ACTIVE' AND l.user_id IS NOT NULL
        """;
    private static final String HOLDERS="""
        SELECT u.user_id,u.full_name,u.email,d.name AS department_name,u.account_status,
          COALESCE(h.devices,0) AS devices,COALESCE(h.components,0) AS components,COALESCE(h.seats,0) AS seats
        FROM users u LEFT JOIN departments d USING(department_id)
        LEFT JOIN (SELECT user_id,COUNT(*) FILTER(WHERE category='DEVICE') AS devices,
           COUNT(*) FILTER(WHERE category='COMPONENT') AS components,
           COALESCE(SUM(seats) FILTER(WHERE category='LICENSE'),0) AS seats
           FROM (
        """+HELD+"""
           ) held GROUP BY user_id) h USING(user_id)
        """;
    private Holder holder(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        return new Holder(r.getLong("user_id"),r.getString("full_name"),r.getString("email"),null,r.getString("department_name"),
            r.getString("account_status"),r.getLong("devices"),r.getLong("components"),r.getLong("seats"));
    }
    private void validate(int page,int size) {
        if(page<0||size<1||size>100) throw new AppException(org.springframework.http.HttpStatus.BAD_REQUEST,"TRANSACTION_REQUEST_INVALID","TRANSACTION_REQUEST_INVALID");
    }
    public PageResponse<Holder> list(String keyword,boolean heldOnly,int page,int size) {
        validate(page,size);
        if(keyword.length()>100) throw new AppException(org.springframework.http.HttpStatus.BAD_REQUEST,"TRANSACTION_REQUEST_INVALID","TRANSACTION_REQUEST_INVALID");
        String search="%"+keyword.trim().toLowerCase(java.util.Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String where=" WHERE (lower(u.full_name) LIKE ? OR lower(u.email) LIKE ?)"+
            (heldOnly?" AND COALESCE(h.devices,0)+COALESCE(h.components,0)+COALESCE(h.seats,0)>0":"");
        long count=jdbc.queryForObject("SELECT count(*) FROM ("+HOLDERS+where+") x",Long.class,search,search);
        var rows=jdbc.query(HOLDERS+where+" ORDER BY lower(u.full_name),u.user_id LIMIT ? OFFSET ?",this::holder,search,search,size,(long)page*size);
        return PageResponse.of(new PageImpl<>(rows,PageRequest.of(page,size),count));
    }
    public Detail detail(Long id,int page,int size) {
        validate(page,size);
        var holder=jdbc.query(HOLDERS+" WHERE u.user_id=?",this::holder,id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND"));
        long count=jdbc.queryForObject("SELECT count(*) FROM ("+HELD+") h WHERE user_id=?",Long.class,id);
        var rows=jdbc.query("SELECT * FROM ("+HELD+") h WHERE user_id=? ORDER BY asset_id,allocation_id NULLS FIRST LIMIT ? OFFSET ?",
            (r,n)->new HeldAsset(r.getLong("asset_id"),r.getString("asset_tag"),r.getString("name"),r.getString("category"),(Long)r.getObject("allocation_id"),r.getInt("seats"),r.getString("status"),r.getString("serial_number"),r.getString("device_tag"),r.getString("device_name")),id,size,(long)page*size);
        return new Detail(holder,PageResponse.of(new PageImpl<>(rows,PageRequest.of(page,size),count)));
    }
}
