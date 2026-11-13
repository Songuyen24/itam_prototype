package com.company.itam.workflow.core.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.workflow.core.dto.WorkflowProgressModels.Group;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Service
public class WorkflowProgressService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public WorkflowProgressService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }
    public List<Group> read(Long id) {
        var rows = jdbc.queryForList("SELECT groups::text FROM workflow_progress WHERE transaction_id=?", String.class, id);
        if (rows.isEmpty()) return List.of();
        try { return mapper.readValue(rows.getFirst(), new TypeReference<List<Group>>() {}); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Invalid workflow progress", ex); }
    }
    public void save(Long id, List<Group> groups) {
        try {
            jdbc.update("INSERT INTO workflow_progress(transaction_id,groups) VALUES (?,?::jsonb) ON CONFLICT(transaction_id) DO UPDATE SET groups=EXCLUDED.groups", id, mapper.writeValueAsString(groups));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    public void requireVersion(TransactionEntity tx, Long expectedVersion) {
        if (!Objects.equals(tx.getContentVersion(), expectedVersion)) {
            var names=jdbc.queryForList("SELECT u.full_name FROM audit_logs a JOIN users u ON u.user_id=a.actor_user_id WHERE a.transaction_id=? OR (a.entity_type='TRANSACTION' AND a.entity_id=?) ORDER BY a.audit_log_id DESC LIMIT 1",String.class,tx.getTransactionId(),tx.getTransactionId());
            throw new AppException(HttpStatus.CONFLICT,"WORKFLOW_CHANGED","WORKFLOW_CHANGED",names.isEmpty()?tx.getRequester().getFullName():names.getFirst());
        }
    }
    public void advance(TransactionEntity tx) { tx.setContentVersion(tx.getContentVersion() + 1); tx.setUpdatedAt(java.time.Instant.now()); }
    public void requireAssetAvailable(Long assetId,Long ownTransaction) {
        var holders=jdbc.queryForList("""
            SELECT a.asset_tag,t.transaction_code,u.full_name FROM (
                SELECT asset_id,transaction_id FROM handover_asset_reservations
                UNION SELECT asset_id,transaction_id FROM recovery_asset_reservations
                UNION SELECT ta.asset_id,t.transaction_id FROM transaction_assets ta JOIN transactions t USING(transaction_id)
                WHERE t.type='DISPOSAL' AND t.status IN ('PENDING','APPROVED','PARTIAL')
                AND (NOT EXISTS(SELECT 1 FROM workflow_progress p WHERE p.transaction_id=t.transaction_id)
                    OR EXISTS(SELECT 1 FROM workflow_progress p CROSS JOIN LATERAL jsonb_array_elements(p.groups) g
                        WHERE p.transaction_id=t.transaction_id AND g->>'status' IN ('PENDING','APPROVED')
                        AND g->'assetIds' @> to_jsonb(ARRAY[ta.asset_id])))
            ) r JOIN assets a USING(asset_id) JOIN transactions t USING(transaction_id) JOIN users u ON u.user_id=t.requester_id
            WHERE r.asset_id=? AND r.transaction_id<>? ORDER BY t.transaction_id LIMIT 1
            """,assetId,ownTransaction==null?-1L:ownTransaction);
        if(!holders.isEmpty()) {
            var holder=holders.getFirst();
            throw new AppException(HttpStatus.CONFLICT,"WORKFLOW_ASSET_RESERVED","WORKFLOW_ASSET_RESERVED",holder.get("asset_tag"),holder.get("transaction_code"),holder.get("full_name"));
        }
    }
    public void requireCapacity(Long licenseId,int seats) {
        var data=jdbc.queryForMap("""
            SELECT a.asset_tag,ld.seat_count,
                COALESCE(SUM(al.seat_count) FILTER(WHERE al.status='ACTIVE'),0) AS active,
                COALESCE(SUM(al.seat_count) FILTER(WHERE al.status='RESERVED'),0) AS reserved,
                (SELECT string_agg(DISTINCT t.transaction_code,', ') FROM license_allocations r JOIN transactions t ON t.transaction_id=r.handover_transaction_id WHERE r.license_asset_id=a.asset_id AND r.status='RESERVED') AS vouchers
            FROM assets a JOIN asset_license_details ld USING(asset_id) LEFT JOIN license_allocations al ON al.license_asset_id=a.asset_id
            WHERE a.asset_id=? GROUP BY a.asset_id,ld.seat_count
            """,licenseId);
        long total=((Number)data.get("seat_count")).longValue(),active=((Number)data.get("active")).longValue(),reserved=((Number)data.get("reserved")).longValue();
        if(total-active-reserved<seats) throw new AppException(HttpStatus.CONFLICT,"WORKFLOW_LICENSE_CAPACITY","WORKFLOW_LICENSE_CAPACITY",data.get("asset_tag"),total,active,reserved,Objects.toString(data.get("vouchers"),"—"));
    }
    public List<Group> requireSelection(List<Group> groups, List<String> ids, String status) {
        if (ids == null || ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) conflict("WORKFLOW_SELECTION_INVALID");
        var selected = groups.stream().filter(g -> ids.contains(g.groupId())).toList();
        if (selected.size() != ids.size() || selected.stream().anyMatch(g -> !status.equals(g.status()))) conflict("WORKFLOW_SELECTION_INVALID");
        return selected;
    }
    private static void conflict(String code) { throw new AppException(HttpStatus.CONFLICT, code, code); }
}
